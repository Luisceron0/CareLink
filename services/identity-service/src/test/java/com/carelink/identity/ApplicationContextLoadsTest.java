package com.carelink.identity;

import com.carelink.identity.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("adminJdbcTemplate")
    private JdbcTemplate adminJdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        EmbeddedPostgresSupport.registerDynamicProperties(registry, "ctxload");
    }

    @Test
    void contextLoads() {
        // Sin aserciones: si el contexto no levanta, el test falla por la excepción.
    }

    /**
     * Dos agujeros reales, encontrados corriendo {@code docker compose up} y probando en
     * este mismo test antes de creerles — no algo que se detectó leyendo el código.
     * Ambos con la misma forma: un bean propio de un tipo que Spring Boot autoconfigura
     * bajo {@code @ConditionalOnMissingBean} hace que la autoconfiguración se abstenga
     * de crear "el otro" bean de ese tipo, dejando un solo candidato (el mío, con
     * credenciales de administrador) para toda inyección sin calificar — incluida la de
     * JPA por debajo. Detalle completo en
     * {@link com.carelink.identity.infrastructure.persistence.DataSourceConfig}.
     *
     * <p>Ningún test de auditoría lo detectó porque ninguno pasa por este cableado: todos
     * construyen su {@code JdbcTemplate} a mano, con credenciales explícitas. Este test
     * es la verificación de que el bean primario tal cual lo arma Spring —no uno armado a
     * mano— conecta con el rol que dice conectar.
     */
    @Test
    void primaryDataSourceConnectsAsRestrictedRoleNotAdmin() {
        String primaryRole = jdbcTemplate.queryForObject("SELECT current_user", String.class);
        String adminRole = adminJdbcTemplate.queryForObject("SELECT current_user", String.class);

        assertThat(primaryRole).isEqualTo(EmbeddedPostgresSupport.APP_ROLE);
        assertThat(adminRole).isEqualTo(EmbeddedPostgresSupport.ADMIN_USER);
        assertThat(primaryRole).as("el rol primario y el administrador no pueden ser el mismo")
                .isNotEqualTo(adminRole);
    }
}
