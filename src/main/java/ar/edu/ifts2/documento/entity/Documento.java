package ar.edu.ifts2.documento.entity;

import ar.edu.ifts2.shared.entity.ContenidoEditorial;
import jakarta.persistence.*;

@Entity
@Table(name = "documentos")
public class Documento extends ContenidoEditorial {
    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", nullable = true, length = 2000)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoDocumento tipo;

    @Column(name = "archivo_object_key", length = 255, unique = true)
    private String archivoObjectKey;

    protected Documento() { }

    public Documento(String titulo, String descripcion, TipoDocumento tipo) { actualizar(titulo, descripcion, tipo); }

    public void actualizar(String titulo, String descripcion, TipoDocumento tipo) {
        this.titulo = titulo == null ? null : titulo.strip();
        this.descripcion = descripcion == null ? null : descripcion.strip();
        this.tipo = tipo;
    }

    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public TipoDocumento getTipo() { return tipo; }
    public String getArchivoObjectKey() { return archivoObjectKey; }
    public void cambiarArchivo(String objectKey) { archivoObjectKey = objectKey; }
}
