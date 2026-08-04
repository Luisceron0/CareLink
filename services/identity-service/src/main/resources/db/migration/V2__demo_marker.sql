-- V2 — Sello de contención (SRS §1.6, §15.2, AC-02).
--
-- La aplicación se niega a arrancar contra una base que no lleve este sello. El
-- propósito no es impedir que un atacante haga algo: es impedir que alguien
-- —incluido el autor— apunte esta aplicación a una base con datos reales por
-- accidente de configuración. Una variable de entorno sola no alcanza, porque
-- viaja con el proceso y no con la base; el sello viaja con la base.
--
-- Una sola fila, garantizado por el tipo: `id` es BOOLEAN con CHECK (id), así que
-- el único valor insertable es TRUE y la clave primaria impide el segundo. No hay
-- estado "dos sellos contradictorios".

CREATE TABLE containment_marker (
    id          BOOLEAN     PRIMARY KEY DEFAULT TRUE,
    stamp       TEXT        NOT NULL,
    stamped_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT containment_marker_single_row CHECK (id),
    CONSTRAINT containment_marker_value      CHECK (stamp = 'SYNTHETIC_DATA_ONLY')
);

INSERT INTO containment_marker (stamp) VALUES ('SYNTHETIC_DATA_ONLY');

COMMENT ON TABLE containment_marker IS
    'Sello de datos sintéticos. Su ausencia hace fallar el arranque (DemoModeGuard, AC-02). '
    'Esta base no debe contener información de salud de personas reales bajo ninguna '
    'circunstancia — SRS §1.6.';
