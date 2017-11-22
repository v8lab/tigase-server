/*
 * AdHocCommandManager.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.component.adhoc;

import tigase.component.adhoc.AdHocResponse.State;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.server.Packet;
import tigase.util.cache.SimpleCache;
import tigase.xml.Element;
import tigase.xmpp.jid.JID;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Bean(name = "adHocCommandManager", active = true)
public class AdHocCommandManager {

	private final Map<String, AdHocCommand> commands = new HashMap<String, AdHocCommand>();
	private final SimpleCache<String, AdHocSession> sessions = new SimpleCache<String, AdHocSession>(100, 10 * 1000);
	@Inject(nullAllowed = true)
	private AdHocCommand[] allCommands;

	public Collection<AdHocCommand> getAllCommands() {
		return this.commands.values();
	}

	public void setAllCommands(AdHocCommand[] allCommands) {
		this.allCommands = allCommands;
		this.commands.clear();
		if (allCommands != null) {
			for (AdHocCommand adHocCommand : allCommands) {
				this.commands.put(adHocCommand.getNode(), adHocCommand);
			}
		}
	}

	public AdHocCommand getCommand(String nodeName) {
		return this.commands.get(nodeName);
	}

	/**
	 * Method checks if exists implementation for this command in this CommandManager
	 *
	 * @param node name for which perform the check
	 *
	 * @return true - if command exists for this node
	 */
	public boolean hasCommand(String node) {
		return this.commands.containsKey(node);
	}

	public Packet process(Packet packet) throws AdHocCommandException {
		final Element element = packet.getElement();
		@SuppressWarnings("unused") final JID senderJid = packet.getStanzaFrom();
		final Element command = element.getChild("command", "http://jabber.org/protocol/commands");
		final String node = command.getAttributeStaticStr("node");
		final String action = command.getAttributeStaticStr("action");
		final String sessionId = command.getAttributeStaticStr("sessionid");
		AdHocCommand adHocCommand = getCommand(node);

		if (adHocCommand == null) {
		} else {
			return process(packet, command, node, action, sessionId, adHocCommand);
		}

		return null;
	}

	public Packet process(Packet packet, Element commandElement, String node, String action, String sessionId,
						  AdHocCommand adHocCommand) throws AdHocCommandException {
		State currentState = null;
		final AdhHocRequest request = new AdhHocRequest(packet, commandElement, node, packet.getStanzaFrom(), action,
														sessionId);
		final AdHocResponse response = new AdHocResponse(sessionId, currentState);
		final AdHocSession session = (sessionId == null) ? new AdHocSession() : this.sessions.get(sessionId);

		adHocCommand.execute(request, response);

		Element commandResult = new Element("command", new String[]{"xmlns", "node",},
											new String[]{"http://jabber.org/protocol/commands", node});

		commandResult.addAttribute("status", response.getNewState().name());
		if ((response.getCurrentState() == null) && (response.getNewState() == State.executing)) {
			this.sessions.put(response.getSessionid(), session);
		} else if ((response.getSessionid() != null) &&
				((response.getNewState() == State.canceled) || (response.getNewState() == State.completed))) {
			this.sessions.remove(response.getSessionid());
		}
		if (response.getSessionid() != null) {
			commandResult.addAttribute("sessionid", response.getSessionid());
		}
		for (Element r : response.getElements()) {
			commandResult.addChild(r);
		}

		return packet.okResult(commandResult, 0);
	}

	public void registerCommand(AdHocCommand command) {
		if (!this.commands.containsKey(command.getNode())) {
			this.commands.put(command.getNode(), command);
		}
	}
}