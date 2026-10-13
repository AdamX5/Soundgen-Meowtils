package net.minecraft.client;
public class Minecraft {
  public net.minecraft.client.entity.EntityPlayerSP thePlayer;
  public net.minecraft.client.multiplayer.WorldClient theWorld;
  public net.minecraft.client.settings.GameSettings gameSettings;
  public boolean inGameHasFocus;
  public net.minecraft.client.gui.GuiScreen currentScreen;
  public static Minecraft getMinecraft() { return null; }
}
