package com.ingsis.snippets.tasks;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Activa las propiedades de las tareas asíncronas y el scheduler del reaper. */
@Configuration
@EnableConfigurationProperties(TasksProperties.class)
@EnableScheduling
public class TasksConfiguration {}
