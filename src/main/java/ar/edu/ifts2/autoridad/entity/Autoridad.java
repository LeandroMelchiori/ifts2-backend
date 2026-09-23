package ar.edu.ifts2.autoridad.entity;

import ar.edu.ifts2.shared.entity.ContenidoEditorial;
import jakarta.persistence.*;

@Entity
@Table(name = "autoridades")
public class Autoridad extends ContenidoEditorial {
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "apellido", nullable = false, length = 100)
    private String apellido;

    @Column(name = "cargo", nullable = false, length = 150)
    private String cargo;

    @Column(name = "descripcion", nullable = true, length = 2000)
    private String descripcion;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "foto_object_key", length = 255, unique = true)
    private String fotoObjectKey;

    protected Autoridad() { }

    public Autoridad(String nombre, String apellido, String cargo, String descripcion, Integer orden) { actualizar(nombre, apellido, cargo, descripcion, orden); }

    public void actualizar(String nombre, String apellido, String cargo, String descripcion, Integer orden) {
        this.nombre = nombre == null ? null : nombre.strip();
        this.apellido = apellido == null ? null : apellido.strip();
        this.cargo = cargo == null ? null : cargo.strip();
        this.descripcion = descripcion == null ? null : descripcion.strip();
        this.orden = orden;
    }

    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getCargo() { return cargo; }
    public String getDescripcion() { return descripcion; }
    public Integer getOrden() { return orden; }
    public String getFotoObjectKey() { return fotoObjectKey; }
    public void cambiarArchivo(String objectKey) { fotoObjectKey = objectKey; }
}
