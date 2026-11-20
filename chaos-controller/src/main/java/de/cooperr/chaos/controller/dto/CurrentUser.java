package de.cooperr.chaos.controller.dto;

/**
 * The logged-in user as the web page needs it.
 *
 * @param name  the user name from Authelia
 * @param admin whether the user may change chaos, containers and load
 */
public record CurrentUser(String name, boolean admin) {
}
