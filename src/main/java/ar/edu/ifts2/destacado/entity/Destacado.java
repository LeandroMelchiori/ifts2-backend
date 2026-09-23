package ar.edu.ifts2.destacado.entity;

import ar.edu.ifts2.evento.entity.Evento;
import ar.edu.ifts2.noticia.entity.Noticia;
import jakarta.persistence.*;

@Entity
@Table(name = "destacados")
public class Destacado {
    @Id
    private Integer posicion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "noticia_id", unique = true)
    private Noticia noticia;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", unique = true)
    private Evento evento;

    protected Destacado() { }

    public Destacado(int posicion, Noticia noticia, Evento evento) {
        this.posicion = posicion;
        this.noticia = noticia;
        this.evento = evento;
    }

    public Integer getPosicion() { return posicion; }
    public Noticia getNoticia() { return noticia; }
    public Evento getEvento() { return evento; }
}
