package com.pgmacdesign.mc3dprint.blueprint;

/** Thrown when blueprint or schematic data is structurally invalid. */
public class BlueprintFormatException extends RuntimeException {
    public BlueprintFormatException(String message) {
        super(message);
    }

    public BlueprintFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
