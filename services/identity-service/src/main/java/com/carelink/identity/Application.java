package com.carelink.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * {@code scanBasePackages} explícito, no el default de {@code @SpringBootApplication}
 * (que escanea solo el paquete de esta clase hacia abajo). Encontrado escribiendo el
 * primer test del dominio clínico: {@code com.carelink.clinical} es HERMANO de
 * {@code com.carelink.identity}, no un sub-paquete — sin esto, ningún bean bajo
 * {@code clinical} se registraba, en ningún entorno, incluida la aplicación real
 * corriendo en compose. Los dos bounded contexts (§3.3) comparten un solo punto de
 * arranque; listar ambos acá es la forma correcta de reflejar eso, no un parche.
 */
@SpringBootApplication(scanBasePackages = {"com.carelink.identity", "com.carelink.clinical"})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
