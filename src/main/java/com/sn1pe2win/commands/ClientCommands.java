package com.sn1pe2win.commands;

import java.sql.SQLException;

import com.devkev.devscript.raw.Block;
import com.devkev.devscript.raw.Command;
import com.devkev.devscript.raw.DataType;
import com.devkev.devscript.raw.Library;
import com.devkev.devscript.raw.Process;
import com.devkev.devscript.raw.Process.HookedLibrary;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sn1pe2win.api.Handshake;
import com.sn1pe2win.api.OAuthHandler.StateAuth;
import com.sn1pe2win.core.Gateway;
import com.sn1pe2win.core.Response;
import com.sn1pe2win.definitions.MembershipType;
import com.sn1pe2win.endpoints.GetProfile;
import com.sn1pe2win.logging.SLogger;
import com.sn1pe2win.main.Exceptions.DestinyAccountAlreadyLinked;
import com.sn1pe2win.main.Exceptions.NoChangesToLinkedAccount;
import com.sn1pe2win.main.Main;
import com.sn1pe2win.sql.simpledb.Exceptions.NoSuchTableException;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import discord4j.core.object.reaction.Reaction;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.rest.util.Color;


/**Commands available to clients
 * This Library only contains the most basic commands.*/
public class ClientCommands extends Library {
	
	public ClientCommands() {
		super("Client Commands");
	}

	@Override
	public Command[] createLib() {
		return new Command[] {
				new Command("help", "", "") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						String message = "";
						
						for(HookedLibrary libs : arg1.getLibraries()) {
							message += "\n[" + libs.name + "]\n";
							for(Command c : libs.commands) {
								message += "  " + c.name + "";
								for(DataType t : c.arguments) {
									message += " <" + t.type.typeName + ">";
								}
								message += "\n";
							}
						}
						
						arg1.log(message, true);
						return null;
					}
				},
				
				new Command("link", "", "") {
					@Override
					public Object execute(Object[] arg0, Process arg1, Block arg2) throws Exception {
						ParsedVariables u = (ParsedVariables) arg1.getVariable("options", arg1.getMain());
						
						Message message = null;
						StateAuth link = Main.remoteAuthetification.requestOAUth(u.invokerAsMember, u.originMessage, new Handshake() {
							
							@Override
							public Response<?> success(OAuthResponseData data) {
								
								//Überprüfe, welche Plattform der user ausgewählt hat
								MembershipType chosen = MembershipType.NONE;
								for(Reaction r : u.botServer.getHost().getBot().getMessageById(data.requestMessage.getChannelId(), data.requestMessage.getId()).block().getReactions()) {
									if(r.getCount() >= 2) {
										String rid = r.getEmoji().asCustomEmoji().get().getId().asString();
										if(rid.equals("796408482983182466")) {
											chosen = MembershipType.PSN;
										} else if(rid.equals("796408520806367282")) {
											chosen = MembershipType.XBOX;
										} else if(rid.equals("796408559541289030")) {
											chosen = MembershipType.PC;
										} else if(rid.equals("796409533634707506")) {
											chosen = MembershipType.STADIA;
										}
										break;
									}
								}
								final MembershipType fchosen = chosen;
								
								//hole die Destiny accountdaten von der bungie website id
								SLogger.INFO.log("Getting Destiny 2 memberships ...", u.botServer.serverID.get());
								Response<JsonObject> response = Gateway.GET("/User/GetBungieAccount/" + data.bungieMembership + "/-1/");
								if(!response.success()) {
									u.invoker.getPrivateChannel().block().createEmbed(spec -> {
										spec.setTitle("Fehler :(");
										spec.setDescription("Konnte deine Destiny 2 Informationen nicht anfragen.\nBitte versuche es erneut");
										spec.addField("Details:", response.toString(), false);
										spec.setColor(Color.RED);
									}).block();
									return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
								}
								
								//Die abfrage war erfolgreich. Jetzt hole alle Plattformen auf der der Spieler spielt
								SLogger.INFO.log("Verifying memberships ...", u.botServer.serverID.get());
								JsonArray destinyMembership = response.getResponseData().getAsJsonObject("Response").getAsJsonArray("destinyMemberships");
								if(destinyMembership == null) {
									u.invoker.getPrivateChannel().block().createEmbed(spec -> {
										spec.setTitle("Fehler :(");
										spec.setDescription("Es wurde kein Destiny 2 Konto gefunden.\nHast du schonmal Destiny 2 gespielt?");
										spec.setColor(Color.of(255, 255, 255));
									}).block();
									return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten.<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
								}
								
								//Wenn es mehrere Konten gibt, überprüfe, ob der user eine Plattform ausgewählt hat
								SLogger.INFO.log("Checking chosen platform ...", u.botServer.serverID.get());
								if(chosen == MembershipType.NONE && destinyMembership.getAsJsonArray().size() > 1) {
									u.invoker.getPrivateChannel().block().createEmbed(spec -> {
										spec.setTitle("Fehler :(");
										spec.setDescription("Bitte wähle als Reaktion eine Plattform aus um dich zu registrieren.\nGib //link ein um einen neuen Link zu generieren und es nochmal zu versuchen!");
										spec.setColor(Color.RED);
									}).block();
									return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten.<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
								}
								
								boolean estimated = false;
							
								for(int i = 0; i < destinyMembership.size(); i++) {
									byte checkPlatform = destinyMembership.get(i).getAsJsonObject().getAsJsonPrimitive("membershipType").getAsByte();
									
									if(checkPlatform == chosen.id || destinyMembership.size() == 1) {
										SLogger.INFO.log("Chosen Platform found on " + MembershipType.of(chosen.id).readable + " !! continuing...", u.botServer.serverID.get());
										
										/**Check again in case the platforms are ambigous*/
										if(destinyMembership.size() == 1 && checkPlatform != chosen.id) {
											SLogger.INFO.log("Platform is estimated", u.botServer.serverID.get());
											estimated = true;
										}
										
										chosen = MembershipType.of(checkPlatform);
										JsonObject destinyProfile = destinyMembership.get(i).getAsJsonObject();
										String did = destinyProfile.getAsJsonPrimitive("membershipId").getAsString();
										MembershipType crosssaveOverride = MembershipType.of(destinyProfile.getAsJsonPrimitive("crossSaveOverride").getAsByte());
										
										if(crosssaveOverride != MembershipType.NONE) {
											for(int j = 0; j < destinyMembership.size(); j++) {
												byte cpOverride = destinyMembership.get(j).getAsJsonObject().getAsJsonPrimitive("membershipType").getAsByte();
												if(cpOverride == crosssaveOverride.id) {
													destinyProfile = destinyMembership.get(j).getAsJsonObject();
													did = destinyProfile.getAsJsonPrimitive("membershipId").getAsString();
													chosen = crosssaveOverride;
													SLogger.INFO.log("Cross save override account found!", u.botServer.serverID.get());
													break;
												}
											}
											SLogger.INFO.log("Crossave override activated!", u.botServer.serverID.get());
										}
										
										//TODO test for the same account
										
										//"test" load the member to check if everything is fine
										SLogger.INFO.log("Verifying id " + did + " on platform " + chosen, u.botServer.serverID.get());
										GetProfile test = new GetProfile(chosen, Long.valueOf(did));
										Response<?> testresponse = test.getDestinyProfileComponent();
										if(!testresponse.success()) {
											if(testresponse.errorCode == 18) {
												String available = "";
												JsonArray applicable = destinyMembership.get(i).getAsJsonObject().getAsJsonArray("applicableMembershipTypes");
												for(int j = 0; j < applicable.size(); j++) 
													available += MembershipType.of(applicable.get(j).getAsJsonPrimitive().getAsByte()) + (j == applicable.size() ? "" : ", ");
												
												//Der zweite Fall dürfte hier eigentlich nie eintreten, da bei nur einer möglichen Plattform diese automatisch ausgewählt wird
												String favailable = available;
												u.invoker.getPrivateChannel().block().createEmbed(spec -> {
													spec.setTitle("Fehler :(");
													spec.setDescription("Es gab einen Fehler dein Destiny Konto auf " + fchosen.readable + " zu finden.\nSicher dass du auf " + fchosen.readable + " spielst?\n"
															+ (applicable.size() > 1 ? "Ich habe folgende Plattformen gefunden, auf denen du spielst: " : "Du solltest folgende Plattform auswählen:") + favailable + "Versuche es nochmal mit //login mit einer anderen Plattform!");
													spec.setColor(Color.RED);
												}).block();
												return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten.<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
											} else {
												u.invoker.getPrivateChannel().block().createEmbed(spec -> {
													spec.setTitle("Fehler :(");
													spec.setDescription("Ein unbekannter Fehler ist bei der Überprüfung deiner Verlinkung aufgetreten.\nBitte versuche es erneut.");
													spec.addField("Details:", testresponse.toString(), false);
													spec.setColor(Color.RED);
												}).block();
												return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten.<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
											}
										}
										
										try {
											u.botServer.linkUser(u.invoker, Long.valueOf(did), chosen, data);
										
											final MembershipType successPlatform = chosen;
											final boolean festimated = estimated;
											u.invoker.getPrivateChannel().block().createEmbed(spec -> {
												spec.setTitle("Erfolg (" + successPlatform.readable + ")");
												//if(previousID == null) 
												spec.setDescription("Der Login war erfolgreich.\nDein Discord Konto wurde mit bungie verbunden!\nDu kannst dein verlinkten Destiny 2 Account jederzeit ändern.\nWenn du die Verlinkung aufheben möchtest, wende dich an Sn1pe2win32.\n\nDeine Verlinkung ist ab sofort für 90 Tage gültig, danach musst du dich erneut verlinken. Du bekommst eine Benachrichtigung wenn es soweit ist.");
												//else spec.setDescription("Dein verknüpftes Konto auf " + u.server.getName() + " wurde erfolgreich geändert!");
												
												if(crosssaveOverride != MembershipType.NONE)  spec.addField("Cross-Save aktiviert", "Cross-Save Verknüpfung mit " + crosssaveOverride.readable + " aktiv!", true);
												if(festimated) spec.addField("Hinweis", "Ich habe kein Destiny Konto auf deiner ausgewählten Plattform gefunden\nDa du nur auf einer Plattform spielst und kein Cross-Save aktiviert hast, habe ich diese Ausgewählt", false);
												spec.setColor(Color.RED);
											}).subscribe();
											return new Response<Object>(null, 500, "Success", "Du hast auf '" + u.server.getName() + "' erfolgreich dein Destiny-Konto angemeldet.<br>Du kannst das Fenster schließen<br>Coole Sache", 0);
										
										} catch(NoChangesToLinkedAccount e1) {
											u.invoker.getPrivateChannel().block().createEmbed(spec -> {
												spec.setTitle("Keine Änderung");
												spec.setDescription("Keine Änderungen an der bestehenden Verlinkung gefunden.\nAlles bleibt beim alten");
												spec.setColor(Color.LIGHT_GRAY);
											}).block();
											return new Response<Object>(null, 500, "BotError", "Keine Änderungen an der bestehenden Verlinkung", 0);
										
										} catch(DestinyAccountAlreadyLinked e2) {
											u.invoker.getPrivateChannel().block().createEmbed(spec -> {
												spec.setTitle("Bereits verlink");
												spec.setDescription("Tut mir Leid, dieser Destiny 2 Account ist schon mit einem anderen Discord Account verlinkt.\nBitte kontaktiere @Sn1pe2win#7106 falls du glaubst, dass das nicht sein kann.");
												spec.setColor(Color.LIGHT_GRAY);
											}).block();
											return new Response<Object>(null, 500, "BotError", "Keine Änderungen an der bestehenden Verlinkung", 0);
											
										} catch (NumberFormatException | SQLException | NoSuchTableException e) {
											e.printStackTrace();
											u.invoker.getPrivateChannel().block().createEmbed(spec -> {
												spec.setTitle("Fehler :(");
												spec.setDescription("Beim letzten Schritt ist ein unerwarteter schwerer Fehler aufgetreten :(\nBitte versuche es erneut.\nWenn der Fehler besteht, bitte kontaktiere @Sn1pe2win#7106");
												spec.addField("Details:", e.getLocalizedMessage(), false);
												spec.setColor(Color.RED);
											}).block();
											return new Response<Object>(null, 500, "BotError", "Beim letzten Schritt ist ein unerwarteter Fehler aufgetreten :(\nBitte versuche es erneut. Wenn der Fehler besteht, bitte kontaktiere @Sn1pe2win#7106", 0);
										}
									}
								}
								
								u.invoker.getPrivateChannel().block().createEmbed(spec -> {
									spec.setTitle("Fehler :(");
									spec.setDescription("Du scheinst noch nie auf " + fchosen.readable + " gespielt zu haben.\nBitte wähle eine andere Plattform aus und versuche es mit '//link' erneut.");
									spec.setColor(Color.RED);
								}).block();
								return new Response<Object>(null, 500, "BotError", "Es ist ein Fehler aufgetreten.<br>Sieh nach, was der Bot dir mitgeteilt hat!", 0);
							}
							
							@Override
							public Response<?> error(String message) {
								u.invoker.getPrivateChannel().block().createEmbed(spec -> {
									spec.setTitle("Fehler :(");
									spec.setDescription("Beim Verlinken ist ein fataler Fehler aufgetreten. Bitte versuche es erneut.\nWenn der Fehler besteht, bitte kontaktiere @Sn1pe2win#7106");
									spec.addField("Details:", message, false);
									spec.setColor(Color.RED);
								}).block();
								return null;
							}
							
						}, 120);
						
						message = u.invoker.getPrivateChannel().block().createEmbed(spec -> {
							spec.setTitle("LOGIN LINK");
							spec.setUrl(link.url);
							spec.setDescription("Um deinen Destiny 2 Account auf dem Server zu verlinken,\n" + 
									"musst du dich mit deinem Konto auf bungie.net einloggen.");
							spec.addField("Plattform Wählen und Einloggen", "Bitte wähle eine Plattform aus, auf der du Destiny spieltst und klicke dann auf den login Link oben oder ***[hier](" + link.url + ")***, um dich einzuloggen.", true);
							spec.setColor(Color.BLUE);
						}).onErrorReturn(null).block();
							
						message.addReaction(ReactionEmoji.custom(Snowflake.of("796408482983182466"), "PSN", false)).block();
						message.addReaction(ReactionEmoji.custom(Snowflake.of("796408520806367282"), "XBOX", false)).block();
						message.addReaction(ReactionEmoji.custom(Snowflake.of("796408559541289030"), "STEAM", false)).block();
						message.addReaction(ReactionEmoji.custom(Snowflake.of("796409533634707506"), "STADIA", false)).block();
						link.message = message;
						return null;
					}
				}
		};
	}

	@Override
	public void scriptExit(Process arg0, int arg1, String arg2) {
		
	}

	@Override
	public void scriptImport(Process arg0) {
		
	}
}
