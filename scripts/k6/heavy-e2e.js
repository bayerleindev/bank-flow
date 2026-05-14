import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';

const accountsUrl = __ENV.ACCOUNTS_URL || 'http://localhost/accounts';
const transferUrl = __ENV.TRANSFER_URL || 'http://localhost/transfer';
const balanceUrl = __ENV.BALANCE_URL || 'http://localhost/balance';

const seedAccounts = intEnv('SEED_ACCOUNTS', 50);
const setupFundingMinor = intEnv('SETUP_FUNDING_MINOR', 100000000);
const setupFundingSleepSeconds = Number(__ENV.SETUP_FUNDING_SLEEP_SECONDS || 15);
const minTransferMinor = intEnv('MIN_TRANSFER_MINOR', 100);
const maxTransferMinor = intEnv('MAX_TRANSFER_MINOR', 5000);
const pspDeclineRate = Number(__ENV.PSP_DECLINE_RATE || 0.02);

const accountRate = intEnv('ACCOUNT_RATE', 10);
const externalRate = intEnv('EXTERNAL_RATE', 150);
const internalRate = intEnv('INTERNAL_RATE', 80);
const balanceReadRate = intEnv('BALANCE_READ_RATE', 200);
const duration = __ENV.DURATION || '10m';
const gracefulStop = __ENV.GRACEFUL_STOP || '60s';

export const options = {
  discardResponseBodies: false,
  scenarios: {
    account_creation: {
      executor: 'constant-arrival-rate',
      exec: 'createAccountScenario',
      rate: accountRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: intEnv('ACCOUNT_PRE_ALLOCATED_VUS', 50),
      maxVUs: intEnv('ACCOUNT_MAX_VUS', 300),
      gracefulStop,
    },
    external_inbound_transfers: {
      executor: 'constant-arrival-rate',
      exec: 'externalInboundScenario',
      rate: externalRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: intEnv('EXTERNAL_PRE_ALLOCATED_VUS', 250),
      maxVUs: intEnv('EXTERNAL_MAX_VUS', 1200),
      gracefulStop,
    },
    internal_transfers_with_psp: {
      executor: 'constant-arrival-rate',
      exec: 'internalTransferScenario',
      rate: internalRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: intEnv('INTERNAL_PRE_ALLOCATED_VUS', 250),
      maxVUs: intEnv('INTERNAL_MAX_VUS', 1200),
      gracefulStop,
    },
    balance_reads: {
      executor: 'constant-arrival-rate',
      exec: 'balanceReadScenario',
      rate: balanceReadRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: intEnv('BALANCE_READ_PRE_ALLOCATED_VUS', 150),
      maxVUs: intEnv('BALANCE_READ_MAX_VUS', 800),
      gracefulStop,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2500', 'p(99)<5000'],
    'checks{flow:account_creation}': ['rate>0.95'],
    'checks{flow:external_inbound}': ['rate>0.95'],
    'checks{flow:internal_transfer}': ['rate>0.90'],
    'checks{flow:balance_read}': ['rate>0.95'],
  },
};

export function setup() {
  const accounts = [];
  for (let index = 0; index < seedAccounts; index += 1) {
    const account = createAccount(`setup-${index}`);
    if (!account || !account.digital_account_id) {
      fail(`failed to create setup account index=${index}`);
    }
    accounts.push(account.digital_account_id);
  }

  for (const accountId of accounts) {
    const response = receiveExternalInbound(accountId, setupFundingMinor, 'setup funding');
    check(response, {
      'setup funding accepted': (r) => r.status >= 200 && r.status < 300,
    });
  }

  if (setupFundingSleepSeconds > 0) {
    sleep(setupFundingSleepSeconds);
  }

  return { accounts };
}

export function createAccountScenario() {
  const response = postJson(
    `${accountsUrl}/accounts`,
    accountPayload(`load-${iterationInTest()}`),
    {
      'Idempotency-Key': `k6-account-${__VU}-${iterationInTest()}-${randomHex(8)}`,
    },
    { flow: 'account_creation', endpoint: 'POST /accounts' },
  );

  check(response, {
    'account created': (r) => r.status >= 200 && r.status < 300 && hasJsonField(r, 'digital_account_id'),
  }, { flow: 'account_creation' });
}

export function externalInboundScenario(data) {
  const destinationId = randomAccount(data.accounts);
  const amountMinor = randomInt(minTransferMinor, maxTransferMinor * 20);
  const response = receiveExternalInbound(destinationId, amountMinor, 'k6 external inbound');

  check(response, {
    'external inbound accepted': (r) => r.status >= 200 && r.status < 300,
  }, { flow: 'external_inbound' });
}

export function internalTransferScenario(data) {
  const [sourceId, destinationId] = randomAccountPair(data.accounts);
  const transferResponse = createTransfer(sourceId, destinationId, randomInt(minTransferMinor, maxTransferMinor));

  const transferOk = check(transferResponse, {
    'internal transfer accepted': (r) => r.status >= 200 && r.status < 300,
  }, { flow: 'internal_transfer' });
  if (!transferOk) {
    return;
  }

  const transfer = parseJson(transferResponse);
  if (!transfer || !transfer.psp_payment_id) {
    check(transferResponse, {
      'internal transfer returned psp payment id': () => false,
    }, { flow: 'internal_transfer' });
    return;
  }

  const webhookResponse = confirmPsp(transfer.psp_payment_id);
  check(webhookResponse, {
    'psp webhook accepted': (r) => r.status >= 200 && r.status < 300,
  }, { flow: 'internal_transfer' });
}

export function balanceReadScenario(data) {
  const accountId = randomAccount(data.accounts);
  const response = http.get(`${balanceUrl}/balances/${accountId}`, {
    tags: { flow: 'balance_read', endpoint: 'GET /balances/{digital_account_id}' },
  });

  check(response, {
    'balance read ok': (r) => r.status === 200 || r.status === 404,
  }, { flow: 'balance_read' });
}

function createAccount(suffix) {
  const response = postJson(
    `${accountsUrl}/accounts`,
    accountPayload(suffix),
    { 'Idempotency-Key': `k6-setup-account-${suffix}-${randomHex(8)}` },
    { flow: 'setup', endpoint: 'POST /accounts' },
  );
  if (response.status < 200 || response.status >= 300) {
    return null;
  }
  return parseJson(response);
}

function createTransfer(sourceId, destinationId, amountMinor) {
  return postJson(
    `${transferUrl}/transfers`,
    {
      source_digital_account_id: sourceId,
      destination_digital_account_id: destinationId,
      amount_minor: amountMinor,
      currency: 'BRL',
      description: 'k6 heavy internal transfer',
    },
    { 'Idempotency-Key': `k6-transfer-${__VU}-${iterationInTest()}-${randomHex(8)}` },
    { flow: 'internal_transfer', endpoint: 'POST /transfers' },
  );
}

function receiveExternalInbound(destinationId, amountMinor, description) {
  return postJson(
    `${transferUrl}/webhooks/external-institutions/transfers`,
    {
      source_institution_code: '999',
      source_institution_name: 'k6 External Institution',
      external_transfer_id: `k6-external-${__VU || 0}-${iterationInTest()}-${randomHex(12)}`,
      destination_digital_account_id: destinationId,
      amount_minor: amountMinor,
      currency: 'BRL',
      description,
    },
    {},
    { flow: 'external_inbound', endpoint: 'POST /webhooks/external-institutions/transfers' },
  );
}

function confirmPsp(pspPaymentId) {
  const failed = Math.random() < pspDeclineRate;
  return postJson(
    `${transferUrl}/webhooks/psp/transfers`,
    {
      psp_payment_id: pspPaymentId,
      status: failed ? 'FAILED' : 'CONFIRMED',
      failure_reason: failed ? 'K6_RANDOM_DECLINE' : null,
    },
    {},
    { flow: 'psp_webhook', endpoint: 'POST /webhooks/psp/transfers' },
  );
}

function postJson(url, body, headers = {}, tags = {}) {
  return http.post(url, JSON.stringify(body), {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...headers,
    },
    tags,
  });
}

function accountPayload(suffix) {
  const unique = `${Date.now()}-${__VU || 0}-${iterationInTest()}-${randomHex(8)}-${suffix}`;
  return {
    fullName: `K6 User ${unique}`,
    documentNumber: generateCpf(),
    email: `k6-${unique}@example.com`,
    motherName: `Mother ${unique}`,
    socialName: `K6 ${suffix}`,
    phoneNumber: `1199${String(randomInt(0, 9999999)).padStart(7, '0')}`,
    birthDate: '18-12-1996',
    address: `Rua K6, ${randomInt(1, 99999)}`,
    isPoliticallyExposed: false,
  };
}

function randomAccount(accounts) {
  return accounts[randomInt(0, accounts.length - 1)];
}

function randomAccountPair(accounts) {
  const sourceIndex = randomInt(0, accounts.length - 1);
  let destinationIndex = randomInt(0, accounts.length - 1);
  while (destinationIndex === sourceIndex) {
    destinationIndex = randomInt(0, accounts.length - 1);
  }
  return [accounts[sourceIndex], accounts[destinationIndex]];
}

function hasJsonField(response, field) {
  const parsed = parseJson(response);
  return parsed && parsed[field] !== undefined && parsed[field] !== null;
}

function parseJson(response) {
  try {
    return response.json();
  } catch (_) {
    return null;
  }
}

function intEnv(name, defaultValue) {
  const value = Number(__ENV[name] || defaultValue);
  return Number.isFinite(value) ? Math.trunc(value) : defaultValue;
}

function iterationInTest() {
  try {
    return exec.scenario.iterationInTest || 0;
  } catch (_) {
    return 0;
  }
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomHex(length) {
  const chars = '0123456789abcdef';
  let value = '';
  for (let index = 0; index < length; index += 1) {
    value += chars[randomInt(0, chars.length - 1)];
  }
  return value;
}

function generateCpf() {
  const digits = [];
  for (let index = 0; index < 9; index += 1) {
    digits.push(randomInt(0, 9));
  }
  const first = cpfCheckDigit(digits);
  const second = cpfCheckDigit([...digits, first]);
  return [...digits, first, second].join('');
}

function cpfCheckDigit(digits) {
  const weight = digits.length + 1;
  const total = digits.reduce((sum, digit, index) => sum + digit * (weight - index), 0);
  const remainder = (total * 10) % 11;
  return remainder === 10 ? 0 : remainder;
}
