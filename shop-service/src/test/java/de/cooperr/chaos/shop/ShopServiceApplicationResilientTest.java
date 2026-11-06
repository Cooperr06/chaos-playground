package de.cooperr.chaos.shop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "shop.resilience-enabled=true")
class ShopServiceApplicationResilientTest {

    @Test
    void contextLoads() {
    }
}
