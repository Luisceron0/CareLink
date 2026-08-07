package com.carelink.identity.infrastructure.persistence;

/**
 * Comillado seguro de identificadores dinámicos (nombres de schema, tabla) para SQL
 * armado por concatenación — ver §8.4: "identificadores dinámicos se validan en el
 * sink". Comillar es la segunda capa, no la única: quien llama debería haber validado
 * ya el valor contra un patrón conocido (p.ej. {@link
 * com.carelink.identity.domain.value.TenantSlug#PATTERN}); esto protege además contra
 * el caso en que esa validación falle o se salte en algún punto — un identificador
 * comillado correctamente no puede escapar hacia SQL ejecutable aunque contenga
 * caracteres que el regex no debería haber dejado pasar.
 */
public final class PostgresIdentifiers {

    private PostgresIdentifiers() {}

    /**
     * Envuelve {@code identifier} en comillas dobles, duplicando cualquier comilla
     * doble interna (el mecanismo de escape que usa Postgres). Un identificador
     * comillado acepta cualquier carácter, incluido el guión — que un identificador
     * SIN comillas no admite (motivo original de este helper: {@code "tenant_" + slug}
     * concatenado sin comillas fallaba con un guión en el slug).
     */
    public static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
