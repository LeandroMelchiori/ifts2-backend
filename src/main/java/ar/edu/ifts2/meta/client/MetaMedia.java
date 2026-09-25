package ar.edu.ifts2.meta.client;

import ar.edu.ifts2.meta.entity.TipoMedia;
import java.net.URI;
import java.time.Instant;

// Solo datos de Graph; la URL temporal no se persiste ni se expone al frontend.
public record MetaMedia(String id, String texto, TipoMedia tipo, Instant fecha, String enlace, URI preview) {
    @Override
    public String toString() { return "MetaMedia[id=" + id + ", tipo=" + tipo + "]"; }
}
