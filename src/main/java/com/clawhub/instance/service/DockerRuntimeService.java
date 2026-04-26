package com.clawhub.instance.service;

import com.clawhub.instance.domain.ManagedInstance;

public interface DockerRuntimeService {

    void createAndStart(ManagedInstance instance);

    void start(ManagedInstance instance);

    void stop(ManagedInstance instance);

    void restart(ManagedInstance instance);

    void delete(ManagedInstance instance);
}
