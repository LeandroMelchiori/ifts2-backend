package ar.edu.ifts2.evento.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evento_fotos")
public class EventoFoto {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;
    @Column(name = "object_key", nullable = false, unique = true, length = 255)
    private String objectKey;
    @Column(nullable = false, length = 200)
    private String etiqueta;
    @Column(nullable = false)
    private int orden;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EventoFoto() { }

    public EventoFoto(Evento evento, String objectKey, String etiqueta, int orden) {
        this.evento = evento;
        this.objectKey = objectKey;
        actualizar(etiqueta, orden);
    }

    public void actualizar(String etiqueta, int orden) {
        this.etiqueta = etiqueta.strip();
        this.orden = orden;
    }

    public void cambiarArchivo(String objectKey) { this.objectKey = objectKey; }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public Evento getEvento() { return evento; }
    public String getObjectKey() { return objectKey; }
    public String getEtiqueta() { return etiqueta; }
    public int getOrden() { return orden; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
