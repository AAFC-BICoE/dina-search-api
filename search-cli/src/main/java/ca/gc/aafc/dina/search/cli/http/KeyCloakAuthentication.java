package ca.gc.aafc.dina.search.cli.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import lombok.Data;

@Component
@Data
public class KeyCloakAuthentication {

  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("expires_in")
  private int expiresIn;

  @JsonProperty("refresh_expires_in")
  private int refreshExpiresIn;

  @JsonProperty("refresh_token")
  private String refreshToken;

  @JsonProperty("token_type")
  private String tokenType;

  @JsonProperty("id_token")
  private String idToken;

  @JsonProperty("not_before_policy")
  private int notBeforePolicy;

  @JsonProperty("session_state")
  private String sessionState;

  private String scope;

}
