package com.carelink.clinical.domain.value;

/**
 * AC-06b — el alcance por servicio (departamento) de una lectura clínica.
 *
 * <p>Existe como tipo, en vez de pasar un {@code String serviceId} nullable, por una
 * razón concreta de seguridad: con un {@code String}, {@code null} significa
 * inevitablemente "sin filtro" (no hay otra cosa que pueda significar), así que
 * CUALQUIER camino que se olvide de setearlo —un bug, un refactor, un endpoint
 * nuevo— falla abierto: devuelve todo el tenant en vez de nada. Con este tipo,
 * "sin filtro" hay que pedirlo explícitamente con {@link #unrestricted()}, y
 * olvidarse de pasarlo es un error de compilación, no una fuga silenciosa.
 *
 * <p>Es el mismo razonamiento que llevó a que {@code SchemaProvisioner} tome
 * {@code TenantSlug} y no {@code String} (AC-05): el tipo hace imposible el uso
 * peligroso, en vez de confiar en que cada llamador se acuerde.
 */
public record ServiceScope(String serviceId, boolean unrestricted) {

    public ServiceScope {
        if (unrestricted && serviceId != null) {
            throw new IllegalArgumentException("Un ServiceScope irrestricto no lleva serviceId");
        }
        if (!unrestricted && (serviceId == null || serviceId.isBlank())) {
            throw new IllegalArgumentException("Un ServiceScope restringido requiere serviceId no vacío");
        }
    }

    /**
     * Ve todo el tenant, sin filtrar por servicio — solo para los roles exentos del §4
     * (TENANT_ADMIN, AUDITOR). Se llama {@code allServices()} y no {@code unrestricted()}
     * porque ese nombre ya lo ocupa el accesor del componente del record.
     */
    public static ServiceScope allServices() {
        return new ServiceScope(null, true);
    }

    /** Ve únicamente los recursos de este servicio. */
    public static ServiceScope of(String serviceId) {
        return new ServiceScope(serviceId, false);
    }
}
