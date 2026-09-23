package ar.edu.ifts2.storage;

public class StorageException extends RuntimeException {
    public enum Reason { INVALID_FILE, TOO_LARGE, UNSUPPORTED_TYPE, NOT_FOUND, PROVIDER_FAILURE }
    private final Reason reason;

    public StorageException(Reason reason) {
        super(switch (reason) {
            case INVALID_FILE -> "Archivo o referencia no validos";
            case TOO_LARGE -> "El archivo supera el tamano permitido";
            case UNSUPPORTED_TYPE -> "Tipo MIME no permitido";
            case NOT_FOUND -> "Objeto no encontrado";
            case PROVIDER_FAILURE -> "No se pudo completar la operacion de almacenamiento";
        });
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
