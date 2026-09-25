package ar.edu.ifts2.destacado.entity;

import ar.edu.ifts2.evento.entity.Evento;
import ar.edu.ifts2.noticia.entity.Noticia;
import ar.edu.ifts2.meta.entity.MetaPost;
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meta_post_id", unique = true)
    private MetaPost metaPost;

    protected Destacado() { }

    public Destacado(int posicion, Noticia noticia, Evento evento) {
        this(posicion, noticia, evento, null);
    }

    public Destacado(int posicion, Noticia noticia, Evento evento, MetaPost metaPost) {
        this.posicion = posicion;
        this.noticia = noticia;
        this.evento = evento;
        this.metaPost = metaPost;
    }

    public Integer getPosicion() { return posicion; }
    public Noticia getNoticia() { return noticia; }
    public Evento getEvento() { return evento; }
    public MetaPost getMetaPost() { return metaPost; }
}
