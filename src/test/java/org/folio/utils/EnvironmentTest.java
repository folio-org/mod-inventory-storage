package org.folio.utils;

import static org.folio.utils.Environment.MAX_REQUEST_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;

class EnvironmentTest {

  @Test
  void testGetKafkaProducerMaxRequestSize_envVar() {
    System.setProperty(MAX_REQUEST_SIZE, "31457280"); // 30MB
    try (var mockedEnv = mockStatic(Environment.class)) {
      mockedEnv.when(() -> Environment.getEnv(MAX_REQUEST_SIZE)).thenReturn("20971520"); // 20MB
      mockedEnv.when(Environment::getKafkaProducerMaxRequestSize).thenCallRealMethod();
      assertEquals(20971520, Environment.getKafkaProducerMaxRequestSize());
    }
  }

  @Test
  void testGetKafkaProducerMaxRequestSize_systemProperty() {
    System.setProperty(MAX_REQUEST_SIZE, "31457280"); // 30MB
    assertEquals(31457280, Environment.getKafkaProducerMaxRequestSize());
    System.clearProperty(MAX_REQUEST_SIZE);
  }

  @Test
  void testGetKafkaProducerMaxRequestSize_defaultValue() {
    assertEquals(10485760, Environment.getKafkaProducerMaxRequestSize()); // 10MB
  }
}