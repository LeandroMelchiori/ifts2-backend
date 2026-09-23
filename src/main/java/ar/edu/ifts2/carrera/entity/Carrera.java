package ar.edu.ifts2.carrera.entity;

import ar.edu.ifts2.shared.entity.ContenidoEditorial;
import jakarta.persistence.*;

@Entity
@Table(name = "carreras")
public class Carrera extends ContenidoEditorial {
    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "titulo_otorgado", nullable = false, length = 200)
    private String tituloOtorgado;

    @Column(name = "descripcion", nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(name = "duracion", nullable = false, length = 100)
    private String duracion;

    @Column(name = "modalidad", nullable = false, length = 100)
    private String modalidad;

    @Column(name = "requisitos_ingreso", nullable = true, columnDefinition = "text")
    private String requisitosIngreso;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @Column(name = "imagen_object_key", length = 255, unique = true)
    private String imagenObjectKey;

    protected Carrera() { }

    public Carrera(String nombre, String tituloOtorgado, String descripcion, String duracion, String modalidad, String requisitosIngreso, Integer orden) { actualizar(nombre, tituloOtorgado, descripcion, duracion, modalidad, requisitosIngreso, orden); }

    public void actualizar(String nombre, String tituloOtorgado, String descripcion, String duracion, String modalidad, String requisitosIngreso, Integer orden) {
        this.nombre = nombre == null ? null : nombre.strip();
        this.tituloOtorgado = tituloOtorgado == null ? null : tituloOtorgado.strip();
        this.descripcion = descripcion == null ? null : descripcion.strip();
        this.duracion = duracion == null ? null : duracion.strip();
        this.modalidad = modalidad == null ? null : modalidad.strip();
        this.requisitosIngreso = requisitosIngreso == null ? null : requisitosIngreso.strip();
        this.orden = orden;
    }

    public String getNombre() { return nombre; }
    public String getTituloOtorgado() { return tituloOtorgado; }
    public String getDescripcion() { return descripcion; }
    public String getDuracion() { return duracion; }
    public String getModalidad() { return modalidad; }
    public String getRequisitosIngreso() { return requisitosIngreso; }
    public Integer getOrden() { return orden; }
    public String getImagenObjectKey() { return imagenObjectKey; }
    public void cambiarArchivo(String objectKey) { imagenObjectKey = objectKey; }
}
