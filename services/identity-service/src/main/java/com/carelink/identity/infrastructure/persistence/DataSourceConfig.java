package com.carelink.identity.infrastructure.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Los dos {@link DataSource} del backend, definidos acá explícitamente y no dejados
 * en manos de la autoconfiguración de Spring Boot.
 *
 * <p><b>Primer intento, roto, verificado contra {@code docker compose up} y no en un
 * test:</b> una versión anterior de esta clase declaraba un solo
 * {@code @Bean DataSource adminDataSource()} para el administrador, asumiendo que
 * Spring Boot seguiría autoconfigurando el primario a partir de
 * {@code spring.datasource.*}. No fue así — {@code DataSourceAutoConfiguration} crea
 * su datasource bajo {@code @ConditionalOnMissingBean(DataSource.class)}, una
 * condición por TIPO. En cuanto existía un bean de tipo {@code DataSource} en el
 * contexto (el administrador), Spring Boot se abstenía de crear el suyo, y el único
 * {@code DataSource} en todo el contexto terminaba siendo el administrador: JPA, y con
 * él todo el tráfico de la aplicación, corrían con privilegios de superusuario. Ningún
 * test lo detectó porque ninguno ejercitaba el bean primario tal cual lo arma Spring.
 *
 * <p><b>Segundo intento, también roto:</b> reemplazar eso por
 * {@code @ConfigurationProperties(prefix="spring.datasource") DataSourceBuilder.create().build()}
 * parecía la solución de manual — hasta que Hibernate falló con "Unable to determine
 * Dialect without JDBC metadata": la URL nunca llegaba al pool. La razón es que
 * {@code @ConfigurationProperties} bindea por reflexión sobre los setters del objeto
 * YA CONSTRUIDO, y {@code HikariDataSource} no tiene un setter {@code setUrl(...)} —
 * tiene {@code setJdbcUrl(...)}. La propiedad {@code spring.datasource.url} nunca se
 * aplicaba, silenciosamente.
 *
 * <p><b>Patrón correcto</b>, el que usa la propia autoconfiguración de Spring Boot
 * internamente: bindear las propiedades sobre {@link DataSourceProperties} (que sí
 * tiene un campo {@code url} con ese nombre exacto) y dejar que
 * {@code initializeDataSourceBuilder()} arme el {@code DataSource} concreto — ese
 * método SÍ sabe traducir {@code url} al setter correcto según la implementación que
 * termine eligiendo (Hikari, Tomcat, etc.), porque usa los métodos semánticos de
 * {@code DataSourceBuilder} ({@code .url(...)}, no binding genérico por reflexión).
 *
 * <p><b>Tercer agujero, mismo patrón un nivel más arriba:</b> con el {@code DataSource}
 * ya resuelto, {@code JdbcTemplateAutoConfiguration} seguía sin crear su bean
 * {@code jdbcTemplate} autoconfigurado — {@code @ConditionalOnMissingBean(JdbcOperations.class)}
 * veía que {@code adminJdbcTemplate} (un bean propio, de tipo {@code JdbcTemplate},
 * que implementa {@code JdbcOperations}) ya existía y se abstenía. Resultado:
 * {@code adminJdbcTemplate} quedaba como el ÚNICO {@code JdbcTemplate} del contexto, y
 * cualquier inyección sin calificar —incluida la de JPA/Hibernate por debajo— resolvía
 * ahí, con el rol administrador. La regla general, confirmada dos veces en esta misma
 * clase: en cuanto se define un bean propio de un tipo que Spring Boot autoconfigura
 * bajo {@code @ConditionalOnMissingBean}, hay que asumir la propiedad de TODOS los
 * beans de ese tipo — dejar que la autoconfiguración cree "el otro" ya no es una
 * opción, porque el bean propio satisface la condición y la autoconfiguración se retira
 * en silencio. Por eso acá abajo el {@code jdbcTemplate} primario también se define a
 * mano, en vez de confiar en que Spring Boot lo siga generando.
 */
@Configuration
public class DataSourceConfig {

    /**
     * Rol restringido (AC-10). Lo usa JPA/Hibernate y el resto del tráfico normal —
     * nunca el rol administrador. {@code carelink_app} no tiene grant de DELETE ni
     * UPDATE sobre {@code audit_log} y no puede crear ni alterar tablas.
     */
    @Primary
    @Bean(name = "dataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "dataSource")
    public DataSource dataSource(@Qualifier("dataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean(name = "jdbcTemplate")
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Mismas credenciales que usa Flyway ({@code carelink.admin-datasource.*} ==
     * {@code spring.flyway.url/user/password}). Un solo consumidor hoy:
     * {@link com.carelink.identity.infrastructure.provisioning.PostgresSchemaProvisioner},
     * que necesita {@code CREATE SCHEMA} y {@code GRANT} al provisionar un tenant —
     * operaciones que el rol restringido no puede hacer, a propósito.
     */
    @Bean(name = "adminDataSourceProperties")
    @ConfigurationProperties(prefix = "carelink.admin-datasource")
    public DataSourceProperties adminDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "adminDataSource")
    public DataSource adminDataSource(@Qualifier("adminDataSourceProperties") DataSourceProperties properties) {
        // Pool chico a propósito: el único consumidor es PostgresSchemaProvisioner, que
        // corre una vez por registro de tenant — un evento raro, no tráfico caliente.
        // El default de Hikari (10) dejaba 10 conexiones administrador ociosas todo el
        // tiempo por cada instancia del backend, sin ningún beneficio a cambio.
        HikariDataSource ds = (HikariDataSource) properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        ds.setPoolName("admin");
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        return ds;
    }

    @Bean(name = "adminJdbcTemplate")
    public JdbcTemplate adminJdbcTemplate(@Qualifier("adminDataSource") DataSource adminDataSource) {
        return new JdbcTemplate(adminDataSource);
    }
}
