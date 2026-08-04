package com.carelink.identity;

import com.carelink.identity.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Levanta el contexto completo de Spring contra una base real y nada más.
 *
 * <p>Este test existe por un agujero concreto: antes de la Sub-fase 0 la suite tenía 22
 * tests en verde sobre una aplicación que no podía arrancar. {@code AuthController}
 * inyectaba {@code VerificationTokenRepository}, un puerto sin ningún adaptador, y
 * ningún test cargaba el contexto — todos instanciaban sus colaboradores a mano o
 * usaban {@code MockMvcBuilders.standaloneSetup}. La falla solo aparecía al hacer
 * {@code docker compose up}.
 *
 * <p>Desde la Sub-fase 1 cubre más que el cableado de beans: al correr contra un
 * PostgreSQL real, cada arranque aplica las migraciones de Flyway y ejecuta
 * {@link com.carelink.identity.infrastructure.containment.DemoModeGuard}. Una migración
 * que no compila, un puerto sin adaptador o un sello de contención ausente rompen acá,
 * en {@code mvn test}, y no en el arranque del contenedor.
 */
@SpringBootTest(properties = {
        "carelink.demo-mode=true",
        "carelink.app-env=test"
})
class ApplicationContextLoadsTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> EmbeddedPostgresSupport.createDatabase("ctxload"));
    }

    @Test
    void contextLoads() {
        // Sin aserciones: si el contexto no levanta, el test falla por la excepción.
    }
}
