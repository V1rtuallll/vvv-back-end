package com.v1rtual.vvv_backend.controller.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
class AdminRouteMappingTest {

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @Test
  void preservesTheExistingAdminEndpointContract() {
    assertMapped("/api/admin/sync-oss-to-db", RequestMethod.POST);
    assertMapped("/api/admin/upload-resource", RequestMethod.POST);
    assertMapped("/api/admin/home/config", RequestMethod.GET);
    assertMapped("/api/admin/home/config", RequestMethod.POST);
    assertMapped("/api/admin/resources", RequestMethod.GET);
    assertMapped("/api/admin/resource/update", RequestMethod.POST);
  }

  private void assertMapped(String path, RequestMethod method) {
    boolean found = handlerMapping.getHandlerMethods().keySet().stream().anyMatch(mapping ->
        mapping.getPatternValues().contains(path)
            && mapping.getMethodsCondition().getMethods().contains(method));
    assertTrue(found, () -> method + " " + path + " must remain mapped");
  }
}
