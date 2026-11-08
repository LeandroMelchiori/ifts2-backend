package ar.edu.ifts2.panelrol.entity;

import java.util.Arrays;

public enum SeccionPanel {
    DESTACADOS("destacados", "/api/admin/destacados"),
    INFORMACION("informacion", "/api/admin/documentos", "/api/admin/institucion",
            "/api/admin/carreras", "/api/admin/autoridades", "/api/admin/enlaces"),
    CONTENIDO("contenido", "/api/admin/noticias", "/api/admin/eventos", "/api/admin/galeria"),
    INSTAGRAM("instagram", "/api/admin/meta");

    private final String id;
    private final String[] paths;

    SeccionPanel(String id, String... paths) { this.id = id; this.paths = paths; }
    public String id() { return id; }
    public String viewAuthority() { return "PANEL_" + id + "_VIEW"; }
    public String manageAuthority() { return "PANEL_" + id + "_MANAGE"; }
    public String[] pathMatchers() {
        return Arrays.stream(paths).flatMap(path -> java.util.stream.Stream.of(path, path + "/**"))
                .toArray(String[]::new);
    }
    public static boolean isKnown(String value) {
        return Arrays.stream(values()).anyMatch(section -> section.id.equals(value));
    }
}
