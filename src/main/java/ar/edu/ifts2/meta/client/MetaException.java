package ar.edu.ifts2.meta.client;

public class MetaException extends RuntimeException {
    public enum Reason { DISABLED, PROVIDER_FAILURE, TOKEN_INVALID, PREVIEW_MISSING }
    private final Reason reason;
    public MetaException(Reason reason) {
        super(switch (reason) {
            case DISABLED -> "La sincronizacion con Meta no esta habilitada";
            case PROVIDER_FAILURE -> "No se pudo completar la consulta a Meta";
            case TOKEN_INVALID -> "La credencial de Meta requiere revision del administrador";
            case PREVIEW_MISSING -> "La publicacion no tiene una imagen de vista previa disponible";
        });
        this.reason = reason;
    }
    public Reason getReason() { return reason; }
}
