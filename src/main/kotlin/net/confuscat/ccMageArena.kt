package net.confuscat

import net.botwithus.internal.scripts.ScriptDefinition
import net.botwithus.rs3.game.Client
import net.botwithus.rs3.game.Area
import net.botwithus.rs3.game.Coordinate
import net.botwithus.rs3.events.impl.ChatMessageEvent
import net.botwithus.rs3.events.impl.InventoryUpdateEvent
import net.botwithus.rs3.game.Inventory
import net.botwithus.rs3.game.Item
import net.botwithus.rs3.game.actionbar.ActionBar
import net.botwithus.rs3.game.inventories.Backpack
import net.botwithus.rs3.game.queries.builders.ItemQuery
import net.botwithus.rs3.game.queries.builders.components.ComponentQuery
import net.botwithus.rs3.game.queries.builders.items.InventoryItemQuery
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
) : LoopingScript (name, scriptConfig, scriptDefinition) {

    private val random: Random = Random()
    var botState: BotState = BotState.IDLE
    var lastChatMessage: String? = null
    var lastNewItem: Item? = null

    //Cupboard states
    val HIDDEN = 1
    val UNHIDDEN = 0
    var currentCupboard = 0

    //Interface IDs
    val INTERFACE_ROTATION = 194
    val INTERFACE_POINTS = 9
    val INTERFACE_BOOTS = 10
    val INTERFACE_SHIELD = 11
    val INTERFACE_HELM = 12
    val INTERFACE_GEM = 13
    val INTERFACE_SWORD = 14

    //Alchable Item IDs
    val alchableSword = 6897
    val alchableGem = 6896
    val alchableHelm = 6895
    val alchableShield = 6894
    val alchableBoots = 6893

    //item names based on id, used to determine what we want to listen in chat for
    private val itemNames = hashMapOf<Int, String> (
        alchableBoots to "Leather boots",
        alchableShield to "Adamant kiteshield",
        alchableHelm to  "Adamant helm",
        alchableGem to "Emerald",
        alchableSword to "Rune longsword"
    )
    //Telekenetic Area
    private var alchArea: Area = Area.Rectangular(Coordinate(3348, 9657, 2), Coordinate(3380, 9615, 2))

    private val itemList = listOf(alchableSword, alchableGem, alchableHelm, alchableShield, alchableBoots)

    private val cupboardIds: Array<IntArray> = arrayOf(
        intArrayOf(10783, 10784), //NW
        intArrayOf(10785, 10786), //West Center North
        intArrayOf(10787, 10788), //West Center South
        intArrayOf(10789, 10790), //SW
        intArrayOf(10791, 10792), //NE
        intArrayOf(10793, 10794), //East Center North
        intArrayOf(10795, 10796), //East Center South
        intArrayOf(10797, 10798)  //SE
    )

    enum class BotState {
        STARTING,
        IDLE,
        SEARCH_AND_ALCH
    }

    override fun initialize(): Boolean {
        super.initialize()
        // Set the script graphics context to our custom one
        this.sgc = ccMageArenaGraphicsContext(this, console)

        if (lastChatMessage == null) {
            subscribe(ChatMessageEvent::class.java) { event ->
                lastChatMessage = event.message
            }
        }
        subscribe(InventoryUpdateEvent::class.java) { event ->
            val newItem = event.newItem
            val oldItem = event.oldItem

            if (newItem == null) return@subscribe             // skip removals
            if (newItem.id == 995 || newItem.id == 8890) return@subscribe // skip coins

            // Only set if it's a new item or item changed
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
            Execution.delay(random.nextLong(2500,5500))
            return
        }
        when (botState) {
            BotState.STARTING -> {
                Execution.delay(startScript(player))
                return
            }
            BotState.IDLE -> {
                println("STATUS: IDLE")
                Execution.delay(random.nextLong(1500,5000))
            }
            BotState.SEARCH_AND_ALCH -> {
                Execution.delay(searchingAndAlching(player))
                return
            }
        }
        return
    }

    private fun getItemValues(): HashMap<Int, Int?> {
        val bootsPrice = ComponentQuery.newQuery(INTERFACE_ROTATION).componentIndex(INTERFACE_BOOTS).results().first()?.text?.toInt()
        val shieldPrice = ComponentQuery.newQuery(INTERFACE_ROTATION).componentIndex(INTERFACE_SHIELD).results().first()?.text?.toInt()
        val helmPrice = ComponentQuery.newQuery(INTERFACE_ROTATION).componentIndex(INTERFACE_HELM).results().first()?.text?.toInt()
        val gemPrice = ComponentQuery.newQuery(INTERFACE_ROTATION).componentIndex(INTERFACE_GEM).results().first()?.text?.toInt()
        val swordPrice = ComponentQuery.newQuery(INTERFACE_ROTATION).componentIndex(INTERFACE_SWORD).results().first()?.text?.toInt()

        println("Boots price: $bootsPrice")
        println("Shield price: $shieldPrice")
        println("Helm price: $helmPrice")
        println("Gem price: $gemPrice")
        println("Sword price: $swordPrice")

        val values: HashMap<Int, Int?> = HashMap<Int, Int?> ()
        values[alchableBoots] = bootsPrice
        values[alchableShield] = shieldPrice
        values[alchableHelm] = helmPrice
        values[alchableGem] = gemPrice
        values[alchableSword] = swordPrice

        return values
    }

    private fun startScript(player: LocalPlayer): Long {
        /* If player is in area then set state to NEW_CUPBOARD */
        if (alchArea.contains(player.coordinate)) {
            println("INFO: Starting Script. Finding new Cupboard")
            botState = BotState.SEARCH_AND_ALCH
        } else {
            println("INFO: Not in Telekentic Area, manually move there")
        }

        return random.nextLong(1000, 1500)
    }

    private fun searchingAndAlching(player: LocalPlayer): Long {

        //grabs values and gets the id and name of the best one (30 coins)
        val itemValues = getItemValues()
        val bestItemId = itemValues.entries.first {it.value == 30}.key
        val bestItemName = itemNames[bestItemId]

        println("Best Item: $bestItemName")
        println("Last Msg: $lastChatMessage")
        println("LastNewItem: ${lastNewItem?.name};${lastNewItem?.id}")

        if (player.isMoving || player.animationId != -1) {
            return random.nextLong(1500, 2000)
        }

        val newItemIndex = itemList.indexOf(lastNewItem?.id)
        val bestItemIndex = itemList.indexOf(bestItemId)

        if (newItemIndex != -1 && bestItemIndex != -1 && newItemIndex != bestItemIndex) {
            //change currentCupboard
            val newItemPositionOffset = newItemIndex - bestItemIndex
            println("Best Item Offset: $newItemPositionOffset")
            currentCupboard = (currentCupboard + newItemPositionOffset).mod(cupboardIds.size)
            println("CurrentCupboard: $currentCupboard")
        }

        Execution.delayWhile(1200) {
            Backpack.interact(bestItemId, "Low alch")
        }

        //cupboards are hidden once opened so we want to get whichever isnt hidden UNHIDDEN/HIDDEN = array index when default cupboard
        //is in the given state
        val cupboard = listOf(
            SceneObjectQuery.newQuery().id(cupboardIds[currentCupboard][UNHIDDEN]).option("Search").results().first(),
            SceneObjectQuery.newQuery().id(cupboardIds[currentCupboard][HIDDEN]).option("Search").results().first()
        ).first { it != null && !it.isHidden }

        if (cupboard?.interact("Search") == true) {
            val initialMessage = lastChatMessage

            Execution.delayUntil(500) {
                lastChatMessage != null && lastChatMessage != initialMessage
            }

            //If cupboard is empty we go to next cupboard, if out of bounds on the array loop back to first cupboard
            if (lastChatMessage?.contains("empty") == true) {
                currentCupboard = (currentCupboard + 1) % cupboardIds.size
            }
            lastChatMessage = null

        }
        return random.nextLong(1500, 2000)
    }

}