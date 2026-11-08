package ar.edu.ifts2.panelrol.dto;

import java.util.Map;

public record PanelPermissionsResponse(boolean fullAccess, String roleName, Map<String, PermisoPanelDto> permissions) { }
