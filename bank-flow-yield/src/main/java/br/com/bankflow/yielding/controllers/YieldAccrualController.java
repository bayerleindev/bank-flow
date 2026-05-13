package br.com.bankflow.yielding.controllers;

import br.com.bankflow.yielding.controllers.dtos.YieldAccrualTriggerResponse;
import br.com.bankflow.yielding.services.YieldAccrualService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class YieldAccrualController {
	private final YieldAccrualService yieldAccrualService;

	public YieldAccrualController(YieldAccrualService yieldAccrualService) {
		this.yieldAccrualService = yieldAccrualService;
	}

	@PostMapping("/yield/accruals/previous-day")
	public ResponseEntity<YieldAccrualTriggerResponse> accruePreviousDay() {
		yieldAccrualService.accruePreviousDay();
		return ResponseEntity.ok(new YieldAccrualTriggerResponse("processed"));
	}
}
