package ar.edu.ifts2.institucion.entity;

import ar.edu.ifts2.shared.entity.ContenidoEditorial;
import jakarta.persistence.*;

@Entity
@Table(name = "institucion")
public class Institucion extends ContenidoEditorial {
    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "descripcion", nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(name = "direccion", nullable = false, length = 300)
    private String direccion;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "telefono", nullable = true, length = 50)
    private String telefono;

    @Column(name = "horarios_atencion", nullable = true, length = 500)
    private String horariosAtencion;

    @Column(nullable = false, unique = true, updatable = false)
    private boolean singleton = true;

    protected Institucion() { }

    public Institucion(String nombre, String descripcion, String direccion, String email, String telefono, String horariosAtencion) { actualizar(nombre, descripcion, direccion, email, telefono, horariosAtencion); }

    public void actualizar(String nombre, String descripcion, String direccion, String email, String telefono, String horariosAtencion) {
        this.nombre = nombre == null ? null : nombre.strip();
        this.descripcion = descripcion == null ? null : descripcion.strip();
        this.direccion = direccion == null ? null : direccion.strip();
        this.email = email == null ? null : email.strip();
        this.telefono = telefono == null ? null : telefono.strip();
        this.horariosAtencion = horariosAtencion == null ? null : horariosAtencion.strip();
    }

    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public String getDireccion() { return direccion; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }
    public String getHorariosAtencion() { return horariosAtencion; }
}
