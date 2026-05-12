# Deploy do Kong API Gateway no Kubernetes usando HTTPRoute

Esta documentação mostra como subir o **Kong API Gateway** em um cluster Kubernetes e expor microsserviços internos usando **Gateway API** com `HTTPRoute`.

O foco aqui é somente o modelo moderno com:

```text
Gateway API + Gateway + HTTPRoute + Kubernetes Service
```

Não será usada a opção via `Ingress`.

---

## 1. Visão geral da arquitetura

A arquitetura esperada é:

```text
Cliente externo
   ↓
LoadBalancer / minikube tunnel
   ↓
Kong API Gateway
   ↓
Gateway API
   ↓
HTTPRoute
   ↓
Kubernetes Service
   ↓
Pods da aplicação
```

No cenário atual, as rotas ficam parecidas com:

```text
/transfer  → bank-flow-transfer
/accounts  → bank-flow-accounts
/balance   → bank-flow-balance
```

O Kong fica responsável por receber o tráfego externo e encaminhar para os `Services` internos do Kubernetes.

---

## 2. Pré-requisitos

Você precisa ter instalado:

```bash
kubectl
helm
```

E precisa ter um cluster Kubernetes ativo, por exemplo:

```bash
minikube
kind
EKS
GKE
AKS
```

Verifique o contexto atual:

```bash
kubectl config current-context
```

Verifique os nodes:

```bash
kubectl get nodes
```

---

## 3. Instalar as CRDs da Gateway API

O `HTTPRoute` faz parte da **Gateway API**, então primeiro é necessário instalar as CRDs no cluster.

```bash
kubectl apply -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.4.1/standard-install.yaml
```

Valide se as CRDs foram instaladas:

```bash
kubectl get crds | grep gateway.networking.k8s.io
```

Você deve ver recursos como:

```text
gatewayclasses.gateway.networking.k8s.io
gateways.gateway.networking.k8s.io
httproutes.gateway.networking.k8s.io
```

---

## 4. Criar namespace do Kong

```bash
kubectl create namespace kong
```

Se o namespace já existir, o comando pode retornar erro. Nesse caso, siga normalmente.

---

## 5. Instalar o Kong via Helm

Adicione o repositório Helm oficial do Kong:

```bash
helm repo add kong https://charts.konghq.com
helm repo update
```

Instale o Kong:

```bash
helm upgrade --install kong kong/ingress \
  -n kong \
  --create-namespace
```

Esse chart instala o Kong Gateway e o Kong Ingress Controller, que também consegue observar recursos da Gateway API, como `Gateway` e `HTTPRoute`.

---

## 6. Validar a instalação do Kong

Verifique os pods:

```bash
kubectl get pods -n kong
```

Você deve ver algo parecido com:

```text
kong-controller-xxxxx
kong-gateway-xxxxx
```

Verifique os services:

```bash
kubectl get svc -n kong
```

Procure pelo service de proxy do Kong, geralmente chamado:

```text
kong-gateway-proxy
```

Ele é o service que recebe o tráfego HTTP/HTTPS externo.

---

## 7. Expor o Kong localmente com Minikube

Se estiver usando Minikube, abra um terminal separado e execute:

```bash
minikube tunnel
```

Depois, em outro terminal, veja o IP externo:

```bash
kubectl get svc -n kong kong-gateway-proxy
```

Exemplo de saída:

```text
NAME                 TYPE           CLUSTER-IP      EXTERNAL-IP     PORT(S)
kong-gateway-proxy   LoadBalancer   10.96.10.123    127.0.0.1       80:30000/TCP,443:30001/TCP
```

Teste o Kong:

```bash
curl -i http://<EXTERNAL-IP>
```

Se ainda não houver nenhuma rota configurada, é normal receber algo parecido com:

```json
{
  "message": "no Route matched with those values"
}
```

Isso significa que o Kong está rodando, mas ainda não existe uma `HTTPRoute` apontando para algum serviço.

---

## 8. Criar o GatewayClass e o Gateway

Crie o arquivo:

```bash
kong-gateway.yaml
```

Conteúdo:

```yaml
apiVersion: gateway.networking.k8s.io/v1
kind: GatewayClass
metadata:
  name: kong
  annotations:
    konghq.com/gatewayclass-unmanaged: "true"
spec:
  controllerName: konghq.com/kic-gateway-controller
---
apiVersion: gateway.networking.k8s.io/v1
kind: Gateway
metadata:
  name: kong
  namespace: kong
spec:
  gatewayClassName: kong
  listeners:
    - name: proxy
      port: 80
      protocol: HTTP
      allowedRoutes:
        namespaces:
          from: All
```

Aplique:

```bash
kubectl apply -f kong-gateway.yaml
```

Valide:

```bash
kubectl get gatewayclass
kubectl get gateway -n kong
```

Você deve ver:

```text
GatewayClass: kong
Gateway: kong
```

---

## 9. Exemplo de services das aplicações

O Kong não aponta diretamente para `Deployment` ou `Pod`.

Ele aponta para `Service`.

Exemplo de service para transferências:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: bank-flow-transfer
  namespace: default
spec:
  type: ClusterIP
  selector:
    app: bank-flow-transfer
  ports:
    - name: http
      port: 8083
      targetPort: 8083
```

Exemplo de service para contas:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: bank-flow-accounts
  namespace: default
spec:
  type: ClusterIP
  selector:
    app: bank-flow-accounts
  ports:
    - name: http
      port: 8084
      targetPort: 8084
```

Exemplo de service para saldo:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: bank-flow-balance
  namespace: default
spec:
  type: ClusterIP
  selector:
    app: bank-flow-balance
  ports:
    - name: http
      port: 8082
      targetPort: 8082
```

Valide se os services existem:

```bash
kubectl get svc -n default
```

Valide se eles possuem endpoints:

```bash
kubectl get endpoints -n default
```

Se um service aparecer sem endpoints, o problema geralmente está no `selector` do service ou nos labels dos pods.

---

## 10. Criar HTTPRoute apontando para os services

Crie o arquivo:

```bash
bank-flow-http-routes.yaml
```

Conteúdo:

```yaml
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: bank-flow-http-routes
  namespace: default
  annotations:
    konghq.com/strip-path: "true"
spec:
  parentRefs:
    - name: kong
      namespace: kong
  rules:
    - matches:
        - path:
            type: PathPrefix
            value: /transfer
      backendRefs:
        - name: bank-flow-transfer
          kind: Service
          port: 8083

    - matches:
        - path:
            type: PathPrefix
            value: /accounts
      backendRefs:
        - name: bank-flow-accounts
          kind: Service
          port: 8084

    - matches:
        - path:
            type: PathPrefix
            value: /balance
      backendRefs:
        - name: bank-flow-balance
          kind: Service
          port: 8082
```

Aplique:

```bash
kubectl apply -f bank-flow-http-routes.yaml
```

Valide:

```bash
kubectl get httproute -n default
kubectl describe httproute bank-flow-http-routes -n default
```

---

## 11. Entendendo o strip-path

A annotation:

```yaml
konghq.com/strip-path: "true"
```

faz o Kong remover o prefixo da rota antes de encaminhar para o serviço interno.

Exemplo:

```text
Cliente chama:
GET /transfer/transfers
```

Com `strip-path: "true"`, o serviço `bank-flow-transfer` recebe:

```text
GET /transfers
```

Outro exemplo:

```text
Cliente chama:
GET /accounts/accounts
```

Com `strip-path: "true"`, o serviço `bank-flow-accounts` recebe:

```text
GET /accounts
```

Use `strip-path: "true"` quando o prefixo externo for apenas um agrupador do gateway.

Use `strip-path: "false"` quando a aplicação interna também espera receber o prefixo.

Exemplo:

```text
Cliente chama:
GET /accounts
```

Com `strip-path: "false"`, o serviço recebe:

```text
GET /accounts
```

---

## 12. Testar as rotas

Pegue o IP externo do Kong:

```bash
kubectl get svc -n kong kong-gateway-proxy
```

Exporte em uma variável:

```bash
export KONG_PROXY_URL=http://<EXTERNAL-IP>
```

Teste a rota de transfer:

```bash
curl -i $KONG_PROXY_URL/transfer
```

Teste a rota de accounts:

```bash
curl -i $KONG_PROXY_URL/accounts
```

Teste a rota de balance:

```bash
curl -i $KONG_PROXY_URL/balance
```

Se suas APIs tiverem health check:

```bash
curl -i $KONG_PROXY_URL/transfer/health
curl -i $KONG_PROXY_URL/accounts/health
curl -i $KONG_PROXY_URL/balance/health
```

Com `strip-path: "true"`, essas chamadas chegam nos serviços como:

```text
/health
/health
/health
```

---

## 13. Adicionar rate limit com KongPlugin

Crie o arquivo:

```bash
bank-flow-rate-limiting.yaml
```

Conteúdo:

```yaml
apiVersion: configuration.konghq.com/v1
kind: KongPlugin
metadata:
  name: bank-flow-rate-limiting
  namespace: default
plugin: rate-limiting
config:
  minute: 100
  policy: local
```

Aplique:

```bash
kubectl apply -f bank-flow-rate-limiting.yaml
```

Agora associe o plugin ao `HTTPRoute`:

```yaml
apiVersion: gateway.networking.k8s.io/v1
kind: HTTPRoute
metadata:
  name: bank-flow-http-routes
  namespace: default
  annotations:
    konghq.com/strip-path: "true"
    konghq.com/plugins: bank-flow-rate-limiting
spec:
  parentRefs:
    - name: kong
      namespace: kong
  rules:
    - matches:
        - path:
            type: PathPrefix
            value: /transfer
      backendRefs:
        - name: bank-flow-transfer
          kind: Service
          port: 8083

    - matches:
        - path:
            type: PathPrefix
            value: /accounts
      backendRefs:
        - name: bank-flow-accounts
          kind: Service
          port: 8084

    - matches:
        - path:
            type: PathPrefix
            value: /balance
      backendRefs:
        - name: bank-flow-balance
          kind: Service
          port: 8082
```

Reaplique:

```bash
kubectl apply -f bank-flow-http-routes.yaml
```

Com essa configuração, o Kong permite até:

```text
100 requests por minuto
```

Depois disso, começa a responder:

```text
HTTP 429 Too Many Requests
```

---

## 14. Testar o rate limit

Execute mais de 100 chamadas dentro do mesmo minuto:

```bash
for i in {1..120}; do
  curl -s -o /dev/null -w "%{http_code}\n" $KONG_PROXY_URL/transfer
 done | sort | uniq -c
```

Resultado esperado:

```text
100 200
 20 429
```

O status de sucesso pode ser diferente de `200`, dependendo da chamada.

O importante é aparecer `429` depois que o limite for excedido.

Para testar mais rápido, da para reduzir temporariamente o plugin para:

```yaml
config:
  minute: 5
  policy: local
```

Depois rode:

```bash
for i in {1..10}; do
  curl -s -o /dev/null -w "%{http_code}\n" $KONG_PROXY_URL/transfer
 done | sort | uniq -c
```

Resultado esperado:

```text
5 200
5 429
```

---

## 15. Observação sobre policy local

Com:

```yaml
policy: local
```

o contador de rate limit fica na memória local do pod do Kong.

Se houver uma única réplica do Kong:

```text
limite real ≈ 100 requests/minuto
```

Se houver três réplicas do Kong:

```text
pod 1: 100 requests/minuto
pod 2: 100 requests/minuto
pod 3: 100 requests/minuto
```

Na prática, o limite total pode ficar próximo de:

```text
300 requests/minuto
```

Para ambiente local e desenvolvimento, `policy: local` é simples e suficiente.

Para produção, precisa de uma política centralizada, dependendo da versão e estratégia de deployment do Kong.

---

## 16. Debug

### Ver pods do Kong

```bash
kubectl get pods -n kong
```

### Ver logs do controller

```bash
kubectl logs -n kong deploy/kong-controller
```

### Ver logs do gateway

```bash
kubectl logs -n kong deploy/kong-gateway
```

### Ver services

```bash
kubectl get svc -A
```

### Ver HTTPRoutes

```bash
kubectl get httproute -A
```

### Descrever uma HTTPRoute

```bash
kubectl describe httproute bank-flow-http-routes -n default
```

### Ver se os services têm endpoints

```bash
kubectl get endpoints -n default
```

Se um service não tiver endpoints, o Kong até pode rotear para ele, mas não haverá pod saudável para receber a requisição.

---

## 17. Problemas comuns

### 17.1. `no Route matched with those values`

Significa que o Kong recebeu a request, mas nenhuma `HTTPRoute` combinou com o path chamado.

Verificar:

```bash
kubectl get httproute -n default
kubectl describe httproute bank-flow-http-routes -n default
```

 Conferir se o path chamado bate com algum `PathPrefix`.

---

### 17.2. `HTTP 404` vindo da aplicação

Se o Kong roteou corretamente, mas a aplicação respondeu `404`, verificar o `strip-path`.

Com:

```yaml
konghq.com/strip-path: "true"
```

A chamada:

```text
/transfer/transfers
```

chega no serviço como:

```text
/transfers
```

Com:

```yaml
konghq.com/strip-path: "false"
```

a mesma chamada chega como:

```text
/transfer/transfers
```

---

### 17.3. Service sem endpoints

Verificarr:

```bash
kubectl get endpoints -n default
```

Se aparecer vazio, comparar o selector do service:

```yaml
selector:
  app: bank-flow-transfer
```

com os labels do pod:

```bash
kubectl get pods -n default --show-labels
```

Os labels precisam bater.

---

### 17.4. `HTTP 429 Too Many Requests`

Significa que o rate limit foi excedido.

Verificar o plugin:

```bash
kubectl get kongplugin -n default
kubectl describe kongplugin bank-flow-rate-limiting -n default
```

Se for ambiente de teste, aumentar o limite ou reduzir a velocidade das chamadas.

---

## 19. Resumo

O fluxo final é:

```text
Cliente
  ↓
Kong Gateway Proxy
  ↓
Gateway API Gateway
  ↓
HTTPRoute
  ↓
Service Kubernetes
  ↓
Pod da aplicação
```

Para criar novas rotas, normalmente so alterar o `HTTPRoute`:

```yaml
rules:
  - matches:
      - path:
          type: PathPrefix
          value: /novo-servico
    backendRefs:
      - name: nome-do-service
        kind: Service
        port: 8080
```

O Kong observa esse recurso e atualiza automaticamente o gateway.
