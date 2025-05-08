package net.confuscat

import net.botwithus.api.game.hud.inventories.Backpack
import net.botwithus.api.game.hud.inventories.Bank
import net.botwithus.internal.scripts.ScriptDefinition
import net.botwithus.rs3.game.*
import net.botwithus.rs3.game.Client.getLocalPlayer
import net.botwithus.rs3.game.hud.interfaces.Interfaces
import net.botwithus.rs3.game.minimenu.MiniMenu
import net.botwithus.rs3.game.minimenu.actions.ComponentAction
import net.botwithus.rs3.game.movement.Movement
import net.botwithus.rs3.game.movement.NavPath
import net.botwithus.rs3.game.movement.TraverseEvent
import net.botwithus.rs3.game.queries.builders.objects.SceneObjectQuery
import net.botwithus.rs3.game.scene.entities.characters.player.LocalPlayer
import net.botwithus.rs3.game.scene.entities.`object`.SceneObject
import net.botwithus.rs3.script.Execution
import net.botwithus.rs3.script.LoopingScript
import net.botwithus.rs3.script.config.ScriptConfig
import java.util.*


class ccMageArena(
    name: String,
    scriptConfig: ScriptConfig,
    scriptDefinition: ScriptDefinition
) : LoopingScript (name, scriptConfig, scriptDefinition) {

    private val random: Random = Random()
    var botState: BotState = BotState.IDLE
    var bankPreset: Int = 10
    val FORTBANK = 125115

    var usePortable: Boolean = false
    var orbTypes = arrayOf("Water", "Earth", "Fire", "Air") //Water, fire, earth, air = 591, 569, 575, 573
    var selectedOrb: Int = 0


    enum class BotState {
        STARTING,
        IDLE,
        SKILLING,
        BANKING,
        MOVING_TO_FORT,
        PROCESSING
    }

    override fun initialize(): Boolean {
        super.initialize()
        // Set the script graphics context to our custom one
        this.sgc = ccMageArenaGraphicsContext(this, console)
        return true
    }

    override fun onLoop() {
        println("STATUS: $botState")
        println(selectedOrb)
        val player = Client.getLocalPlayer()
        if (Client.getGameState() != Client.GameState.LOGGED_IN || player == null || botState == BotState.IDLE) {
            Execution.delay(random.nextLong(2500,5500))
            return
        }
        when (botState) {
            BotState.STARTING -> {
                Execution.delay(startScript(player))
                return
            }
            BotState.SKILLING -> {
                Execution.delay(handleSkilling())
                return
            }
            BotState.BANKING -> {
                Execution.delay(handleBanking())
                return
            }
            BotState.MOVING_TO_FORT -> {
                Execution.delay(moveToFort())
                return
            }
            BotState.IDLE -> {
                println("STATUS: IDLE")
                Execution.delay(random.nextLong(1500,5000))
            }
            BotState.PROCESSING -> {
                println("STATUS: PROCESSING")
                Execution.delay(craftStaff())
                return
            }
        }
        return
    }

    private fun startScript(player: LocalPlayer): Long {
        val fortArea: Area = Area.Rectangular(Coordinate(3276, 3561, 0), Coordinate(3284, 3549, 0))
        if (!fortArea.contains(player.coordinate)) {
            println("Starting moving state")
            botState = BotState.MOVING_TO_FORT
        } else {
            println("Banking")
            botState = BotState.BANKING
        }
        return random.nextLong(1500, 3500)
    }

    private fun moveToFort(): Long {
        println("Moving to Fort Forinthry")

        val fortArea: Area = Area.Rectangular(Coordinate(3278, 3556, 0), Coordinate(3282, 3553, 0))
        val coords: Coordinate = fortArea.randomWalkableCoordinate
        val path = NavPath.resolve(coords).interrupt { event: TraverseEvent? ->
                    coords.isReachable && coords.distanceTo(
                        getLocalPlayer()!!.coordinate
                    ) <= 2
                }
        val moveState: TraverseEvent.State = Movement.traverse(path)

        if (moveState == TraverseEvent.State.FINISHED) {
            println("Traversal success -> banking")
            botState = BotState.BANKING
        } else {
            println("Traversal Failed -> retrying")
            botState = BotState.MOVING_TO_FORT
        }
        return random.nextLong(1500, 3500)
    }

    private fun handleBanking(): Long {
        if (Bank.isOpen()) {
            println("Loading preset $bankPreset")
            Bank.loadPreset(bankPreset)
            Execution.delayWhile(1500){
                Bank.isOpen()
            }
            botState = BotState.SKILLING
        } else {
            Bank.open()
            Execution.delayUntil(1500){
                Bank.isOpen()
            }

        }
        return random.nextLong(1500, 3000)
    }

    private fun craftStaff(): Long{
        Execution.delayUntil(
            15000
        ) { Interfaces.isOpen(1370) }
        if (Interfaces.isOpen(1370)) {

            MiniMenu.interact(ComponentAction.DIALOGUE.type, 0, -1, 89784350)
            botState = BotState.PROCESSING

            Execution.delayUntil(
                15000
            ) { Interfaces.isOpen(1251) }

            while (Interfaces.isOpen(1251)){
                Execution.delay(1000)
            }

            println("Finished crafting, banking now")
            botState = BotState.BANKING
        }
        return random.nextLong(1500, 3000)
    }

    private fun handleSkilling(): Long {
        println("Crafting staves")

        var orbName: String = orbTypes[selectedOrb]
        var orbId: Int = 591

        when(selectedOrb){
            0 -> { orbId = 591 }
            1 -> { orbId = 569 }
            2 -> { orbId = 575 }
            3 -> { orbId = 573 }
        }

        if (!Backpack.contains(orbId)){
            println("Cannot find $orbName in inventory")
            botState = BotState.IDLE
        }
        println("Found $orbName")
        if (usePortable) {
            println("looking for portable")
            val portable: SceneObject? =
                SceneObjectQuery.newQuery().name("Portable crafter").option("Craft").results().nearest()
            portable?.interact("Craft")
            botState = BotState.PROCESSING
            return random.nextLong(1500, 3000)
        }


        Backpack.interact("$orbName orb")
        botState = BotState.PROCESSING

        return random.nextLong(1500, 3000)
    }
}