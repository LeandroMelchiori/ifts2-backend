package ar.edu.ifts2.meta.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "meta_posts")
public class MetaPost {
    @Id
    @Column(length = 40)
    private String id;
    @Column(nullable = false, columnDefinition = "text")
    private String texto;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMedia mediaType;
    @Column(nullable = false)
    private Instant fechaPublicacion;
    @Column(nullable = false, length = 2048)
    private String linkOriginal;
    @Column(nullable = false)
    private boolean visible;
    @Column(length = 255)
    private String objectKey;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected MetaPost() { }
    public MetaPost(String id) { this.id = id; }

    public void actualizar(String texto, TipoMedia tipo, Instant fecha, String enlace) {
        this.texto = texto;
        this.mediaType = tipo;
        this.fechaPublicacion = fecha;
        this.linkOriginal = enlace;
    }
    public void cambiarVisibilidad(boolean visible) { this.visible = visible; }
    public void asociarArchivo(String key) { this.objectKey = key; }
    @PrePersist
    void create() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate
    void update() { updatedAt = Instant.now(); }
    public String getId() { return id; }
    public String getTexto() { return texto; }
    public TipoMedia getMediaType() { return mediaType; }
    public Instant getFechaPublicacion() { return fechaPublicacion; }
    public String getLinkOriginal() { return linkOriginal; }
    public boolean isVisible() { return visible; }
    public String getObjectKey() { return objectKey; }
}
