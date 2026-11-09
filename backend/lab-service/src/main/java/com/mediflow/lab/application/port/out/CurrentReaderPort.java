package com.mediflow.lab.application.port.out;

import java.util.Optional;

public interface CurrentReaderPort {
    Optional<ReaderIdentity> current();
}
