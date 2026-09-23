package ar.edu.ifts2.noticia.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "noticias")
public class Noticia {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String resumen;

    @Column(nullable = false, columnDefinition = "text")
    private String contenido;

    @Column(name = "portada_object_key", length = 255, unique = true)
    private String portadaObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoNoticia estado = EstadoNoticia.BORRADOR;

    @Column(name = "publicada_at")
    private Instant publicadaAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Noticia() { }

    public Noticia(String titulo, String resumen, String contenido) {
        actualizar(titulo, resumen, contenido);
    }

    public void actualizar(String titulo, String resumen, String contenido) {
        this.titulo = titulo.strip();
        this.resumen = resumen.strip();
        this.contenido = contenido.strip();
    }

    public void cambiarEstado(EstadoNoticia nuevoEstado) {
        if (nuevoEstado == EstadoNoticia.PUBLICADA && estado != EstadoNoticia.PUBLICADA) {
            publicadaAt = Instant.now();
        }
        estado = nuevoEstado;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getResumen() { return resumen; }
    public String getContenido() { return contenido; }
    public String getPortadaObjectKey() { return portadaObjectKey; }
    public void cambiarPortada(String objectKey) { portadaObjectKey = objectKey; }
    public EstadoNoticia getEstado() { return estado; }
    public Instant getPublicadaAt() { return publicadaAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
