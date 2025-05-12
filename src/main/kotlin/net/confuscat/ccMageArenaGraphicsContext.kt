package net.confuscat

import net.botwithus.rs3.imgui.ImGui
import net.botwithus.rs3.script.ScriptConsole
import net.botwithus.rs3.script.ScriptGraphicsContext

class ccMageArenaGraphicsContext(
    private val script: ccMageArena,
    console: ScriptConsole
) : ScriptGraphicsContext (console) {

    override fun drawSettings() {
        super.drawSettings()
        ImGui.Begin("ccMageArena", 0)
        ImGui.Text("Bot Sate: ${script.botState}")
        if (ImGui.Button("Start")) {
            script.botState = ccMageArena.BotState.STARTING;
            script.println("Starting...")
        }
        ImGui.End()
    }

    override fun drawOverlay() {
        super.drawOverlay()
    }

}