package ar.edu.ifts2.evento.entity;

import ar.edu.ifts2.shared.entity.ContenidoEditorial;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "eventos")
public class Evento extends ContenidoEditorial {
    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "resumen", nullable = false, length = 500)
    private String resumen;

    @Column(name = "descripcion", nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(name = "fecha_inicio", nullable = false)
    private Instant fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private Instant fechaFin;

    @Column(name = "lugar", nullable = false, length = 300)
    private String lugar;

    @Column(name = "portada_object_key", length = 255, unique = true)
    private String portadaObjectKey;

    protected Evento() { }

    public Evento(String titulo, String resumen, String descripcion, Instant fechaInicio, Instant fechaFin, String lugar) { actualizar(titulo, resumen, descripcion, fechaInicio, fechaFin, lugar); }

    public void actualizar(String titulo, String resumen, String descripcion, Instant fechaInicio, Instant fechaFin, String lugar) {
        this.titulo = titulo == null ? null : titulo.strip();
        this.resumen = resumen == null ? null : resumen.strip();
        this.descripcion = descripcion == null ? null : descripcion.strip();
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.lugar = lugar == null ? null : lugar.strip();
    }

    public String getTitulo() { return titulo; }
    public String getResumen() { return resumen; }
    public String getDescripcion() { return descripcion; }
    public Instant getFechaInicio() { return fechaInicio; }
    public Instant getFechaFin() { return fechaFin; }
    public String getLugar() { return lugar; }
    public String getPortadaObjectKey() { return portadaObjectKey; }
    public void cambiarArchivo(String objectKey) { portadaObjectKey = objectKey; }
}
