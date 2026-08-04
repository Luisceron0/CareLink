package com.carelink.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Levanta el contexto completo de Spring y nada más.
 *
 * Este test existe por un agujero concreto: hasta la Sub-fase 0 la suite tenía 22
 * tests en verde sobre una aplicación que no podía arrancar. `AuthController`
 * inyectaba `VerificationTokenRepository`, un puerto sin ningún adaptador, y
 * ningún test cargaba el contexto — todos instanciaban sus colaboradores a mano o
 * usaban `MockMvcBuilders.standaloneSetup`. La falla solo aparecía al hacer
 * `docker compose up`.
 *
 * Un puerto sin adaptador, un bean duplicado o una dependencia circular tienen que
 * romper acá, en `mvn test`, no en el arranque del contenedor.
 *
 * La URL de base de datos apunta a un destino inexistente a propósito: con
 * `ddl-auto: none` y el dialecto declarado, Hibernate no necesita conectarse para
 * armar el contexto. Lo que se verifica es el cableado de beans, no la base.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:1/contextloadsonly",
        "spring.datasource.username=none",
        "spring.datasource.password=none",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.sql.init.mode=never"
})
class ApplicationContextLoadsTest {

    @Test
    void contextLoads() {
        // Sin aserciones: si el contexto no levanta, el test falla por la excepción.
    }
}
