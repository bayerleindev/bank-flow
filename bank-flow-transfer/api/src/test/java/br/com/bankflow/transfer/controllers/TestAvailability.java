package br.com.bankflow.transfer.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TestAvailability {
    @Test
    void test() {
        try {
            Class.forName("org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc");
            System.out.println("AutoConfigureMockMvc found");
        } catch (ClassNotFoundException e) {
            System.out.println("AutoConfigureMockMvc NOT found");
        }
    }
}
