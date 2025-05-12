package net.confuscat

import net.botwithus.internal.scripts.ScriptDefinition
import net.botwithus.rs3.game.Client
import net.botwithus.rs3.game.Area
import net.botwithus.rs3.game.Coordinate
import net.botwithus.rs3.events.impl.ChatMessageEvent
import net.botwithus.rs3.events.impl.InventoryUpdateEvent
import net.botwithus.rs3.game.Item
import net.botwithus.rs3.game.inventories.Backpack
import net.botwithus.rs3.game.queries.builders.components.ComponentQuery
import net.botwithus.rs3.game.queries.builders.objects.SceneObjectQuery
import net.botwithus.rs3.game.scene.entities.characters.player.LocalPlayer
import net.botwithus.rs3.script.Execution
import net.botwithus.rs3.script.LoopingScript
import net.botwithus.rs3.script.config.ScriptConfig
import java.util.*

class ccMageArena(
    name: String,
    scriptConfig: ScriptConfig,
    scriptDefinition: ScriptDefinition
) : LoopingScript(name, scriptConfig, scriptDefinition) {

    private val random: Random = Random()
    var botState: BotState = BotState.IDLE
    var lastChatMessage: String? = null
    var lastNewItem: Item? = null
    var toAlch: Int = -1

    val HIDDEN = 1
    val UNHIDDEN = 0
    var currentCupboard = random.nextInt(0, 8)

    val INTERFACE_ROTATION = 194
    val INTERFACE_POINTS = 9
    val INTERFACE_BOOTS = 10
    val INTERFACE_SHIELD = 11
    val INTERFACE_HELM = 12
    val INTERFACE_GEM = 13
    val INTERFACE_SWORD = 14

    val alchableSword = 6897
    val alchableGem = 6896
    val alchableHelm = 6895
    val alchableShield = 6894
    val alchableBoots = 6893

    private val itemNames = hashMapOf(
        alchableBoots to "Leather boots",
        alchableShield to "Adamant kiteshield",
        alchableHelm to "Adamant helm",
        alchableGem to "Emerald",
        alchableSword to "Rune longsword"
    )

    private val alchArea = Area.Rectangular(Coordinate(3348, 9657, 2), Coordinate(3380, 9615, 2))

    private val itemList = listOf(alchableSword, alchableGem, alchableHelm, alchableShield, alchableBoots)

    private val cupboardIds: Array<IntArray> = arrayOf(
        intArrayOf(10783, 10784), intArrayOf(10785, 10786), intArrayOf(10787, 10788),
        intArrayOf(10789, 10790), intArrayOf(10791, 10792), intArrayOf(10793, 10794),
        intArrayOf(10795, 10796), intArrayOf(10797, 10798)
    )

    private val cupboardToItemRank = mutableMapOf<Int, Int>()

    enum class BotState {
        STARTING,
        IDLE,
        SEARCHING,
        TAKE_AND_ALCH
    }

    override fun initialize(): Boolean {
        super.initialize()
        this.sgc = ccMageArenaGraphicsContext(this, console)

        subscribe(ChatMessageEvent::class.java) { event ->
            if (event.messageType == 0) {
                lastChatMessage = event.message
            }
        }

        subscribe(InventoryUpdateEvent::class.java) { event ->
            val newItem = event.newItem
            val oldItem = event.oldItem

            if (newItem == null || newItem.id == -1) return@subscribe
            if (newItem.id == 995 || newItem.id == 8890) return@subscribe

            if (oldItem == null || oldItem.id != newItem.id) {
                lastNewItem = newItem
                println("New cupboard item: ${newItem.name} (${newItem.id})")
            }
        }

        return true
    }

    override fun onLoop() {
        println("STATUS: $botState")

        val player = Client.getLocalPlayer()
        if (Client.getGameState() != Client.GameState.LOGGED_IN || player == null || botState == BotState.IDLE) {
            Execution.delay(random.nextLong(2500, 5500))
            return
        }

        when (botState) {
            BotState.STARTING -> Execution.delay(startScript(player))
            BotState.SEARCHING -> Execution.delay(searching(player))
            BotState.TAKE_AND_ALCH -> Execution.delay(takeAndAlch())
            BotState.IDLE -> Execution.delay(random.nextLong(1500, 5000))
        }
    }

    private fun getItemValues(): HashMap<Int, Int?> {
        val values = hashMapOf<Int, Int?>()
        values[alchableBoots] = ComponentQuery.newQuery(INTERFACE_ROTATION).componentIndex(INTERFACE_BOOTS).results().first()?.text?.toInt()
        values[alchableShield] = ComponentQuery.newQuery(INTERFACE_ROTATION).componentIndex(INTERFACE_SHIELD).results().first()?.text?.toInt()
        values[alchableHelm] = ComponentQuery.newQuery(INTERFACE_ROTATION).componentIndex(INTERFACE_HELM).results().first()?.text?.toInt()
        values[alchableGem] = ComponentQuery.newQuery(INTERFACE_ROTATION).componentIndex(INTERFACE_GEM).results().first()?.text?.toInt()
        values[alchableSword] = ComponentQuery.newQuery(INTERFACE_ROTATION).componentIndex(INTERFACE_SWORD).results().first()?.text?.toInt()

        values.forEach { (id, price) -> println("${itemNames[id]} price: $price") }
        return values
    }

    private fun bestItemCheck() {
        val itemValues = getItemValues()
        val bestItemId = itemValues.entries.firstOrNull { it.value == 30 }?.key ?: return
        toAlch = bestItemId
        val bestRank = itemList.indexOf(bestItemId)

        val newItemId = lastNewItem?.id ?: return
        val currentRank = itemList.indexOf(newItemId)

        if (currentRank == -1 || bestRank == -1) return

        if (currentRank == bestRank) {
            println("Found Best Item: ${itemNames[bestItemId]}; $bestItemId")
            botState = BotState.TAKE_AND_ALCH
        } else {
            val offset = currentRank - bestRank
            println("Found ${itemNames[newItemId]} at cupboard $currentCupboard (rank $currentRank)")
            println("Best item is ${itemNames[bestItemId]} (rank $bestRank), offset = $offset")
            currentCupboard = (currentCupboard + offset).mod(cupboardIds.size)
            println("Adjusted cupboard to $currentCupboard")
            botState = BotState.SEARCHING
        }
    }

    private fun handleCupboardResult() {
        val initialMessage = lastChatMessage

        if (Execution.delayUntil(1000) {
                lastChatMessage != null && lastChatMessage != initialMessage
            }) {
            Execution.delay(150)
        }

        if (lastChatMessage?.contains("empty") == true) {
            println("Next cupboard")
            currentCupboard = (currentCupboard + 1) % cupboardIds.size
        } else {
            lastNewItem?.let {
                val rank = itemList.indexOf(it.id)
                if (rank != -1) {
                    cupboardToItemRank[currentCupboard] = rank
                    println("Mapped cupboard $currentCupboard rank $rank (${it.name})")
                }
            }
            bestItemCheck()
        }

        lastChatMessage = null
    }

    private fun startScript(player: LocalPlayer): Long {
        if (alchArea.contains(player.coordinate)) {
            println("INFO: Starting Script.")
            botState = BotState.SEARCHING
        } else {
            println("INFO: Not in alchemy Area, manually move there")
        }
        return random.nextLong(1000, 1500)
    }

    private fun searching(player: LocalPlayer): Long {
        if (player.isMoving) {
            println("Moving to new cupboard")
            return random.nextLong(1500, 2000)
        }

        println("INFO: Looking for new item location")

        val cupboard = listOf(
            SceneObjectQuery.newQuery().id(cupboardIds[currentCupboard][UNHIDDEN]).option("Search").results().first(),
            SceneObjectQuery.newQuery().id(cupboardIds[currentCupboard][HIDDEN]).option("Search").results().first()
        ).first { it != null && !it.isHidden }

        lastNewItem = null

        if (cupboard?.interact("Search") == true) {
            handleCupboardResult()
        }

        return random.nextLong(1000, 1500)
    }

    private fun takeAndAlch(): Long {
        if (toAlch != -1) {
            Execution.delayWhile(1300) {
                Backpack.interact(toAlch, "Low alch")
            }

            val cupboard = listOf(
                SceneObjectQuery.newQuery().id(cupboardIds[currentCupboard][UNHIDDEN]).option("Search").results().first(),
                SceneObjectQuery.newQuery().id(cupboardIds[currentCupboard][HIDDEN]).option("Search").results().first()
            ).first { it != null && !it.isHidden }

            lastNewItem = null

            if (cupboard?.interact("Search") == true) {
                handleCupboardResult()
            }
        }

        return random.nextLong(600, 800)
    }
}
