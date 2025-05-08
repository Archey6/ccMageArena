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
        ImGui.Begin("ccBattlestaves", 0)
        ImGui.SetWindowSize(250f, 200f)
        ImGui.Text("ccBattlestaves")
        ImGui.Text("Bot Sate: ${script.botState}")
        if (ImGui.Button("Start")) {
            script.botState = ccMageArena.BotState.STARTING;
            script.println("Starting...")
        }

        script.bankPreset = ImGui.InputInt("Bank preset", script.bankPreset)
        script.usePortable = ImGui.Checkbox("Use portable", script.usePortable)
        script.selectedOrb = ImGui.Combo("Orb Type:", script.selectedOrb, *script.orbTypes)
        ImGui.End()
    }

    override fun drawOverlay() {
        super.drawOverlay()
    }

}