package com.mediflow.surgery.application.port.out;

import java.util.function.Supplier;

/** Explicit transaction boundaries; remote authority calls belong only in outside(). */
public interface SurgeryUnitOfWorkPort {
    <T> T read(Supplier<T> action);
    <T> T write(Supplier<T> action);
    <T> T outside(Supplier<T> action);
}
