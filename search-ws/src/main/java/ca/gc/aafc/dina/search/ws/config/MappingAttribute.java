package ca.gc.aafc.dina.search.ws.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@RequiredArgsConstructor
@Getter
@Setter
public class MappingAttribute {

  public enum DateSubtype {LOCAL_DATE, LOCAL_DATE_TIME, DATE_TIME, DATE_TIME_OPTIONAL_TZ}

  @NotBlank
  private String name;

  @NotBlank
  private String type;

  private Boolean distinctTermAgg;
  private DateSubtype dateSubtype;
}
