package ar.edu.ifts2.enlace.entity;

import ar.edu.ifts2.shared.entity.ContenidoEditorial;
import jakarta.persistence.*;

@Entity
@Table(name = "enlaces")
public class Enlace extends ContenidoEditorial {
    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descripcion", nullable = true, length = 500)
    private String descripcion;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "categoria", nullable = false, length = 100)
    private String categoria;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    protected Enlace() { }

    public Enlace(String titulo, String descripcion, String url, String categoria, Integer orden) { actualizar(titulo, descripcion, url, categoria, orden); }

    public void actualizar(String titulo, String descripcion, String url, String categoria, Integer orden) {
        this.titulo = titulo == null ? null : titulo.strip();
        this.descripcion = descripcion == null ? null : descripcion.strip();
        this.url = url == null ? null : url.strip();
        this.categoria = categoria == null ? null : categoria.strip();
        this.orden = orden;
    }

    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public String getUrl() { return url; }
    public String getCategoria() { return categoria; }
    public Integer getOrden() { return orden; }
}
