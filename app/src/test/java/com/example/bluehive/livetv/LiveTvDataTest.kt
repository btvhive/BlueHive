package com.example.bluehive.livetv

import com.example.bluehive.api.LiveChannelDto
import com.example.bluehive.api.LiveRowDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTvDataTest {

    private fun ch(id: String, row: String, status: String? = "ok", backup: String? = null) =
        LiveChannelDto(id, id.uppercase(), row, "UK", "FHD", backup, status)

    private val server = listOf(
        LiveRowDto("Motorsport", "#D64545", listOf(ch("f1", "Motorsport", backup = "f1-b"), ch("motogp", "Motorsport"))),
        LiveRowDto("News", "#D9A21B", listOf(ch("cnn", "News", status = "off"))),
        LiveRowDto("Kids", "#E0559A", listOf(ch("disney", "Kids"), ch("nick", "Kids"))),
        LiveRowDto("Empty", "#000000", emptyList()),
    )

    @Test
    fun numbersLikeACableBox_rowTimes100PlusPosition() {
        val rows = LiveTvData.buildRows(server, kids = false, recentIds = emptyList())
        assertEquals(listOf("Motorsport", "News", "Kids"), rows.map { it.name })
        assertEquals(listOf(101, 102), rows[0].channels.map { it.number })
        assertEquals(listOf(201), rows[1].channels.map { it.number })
        assertEquals(listOf(301, 302), rows[2].channels.map { it.number })
    }

    @Test
    fun kidsProfile_seesOnlyTheKidsRow_andOnlyKidsRecents() {
        val rows = LiveTvData.buildRows(server, kids = true, recentIds = listOf("cnn", "nick"))
        assertEquals(listOf(LiveTvData.RECENT_ROW, "Kids"), rows.map { it.name })
        assertEquals(listOf("nick"), rows[0].channels.map { it.id })
    }

    @Test
    fun recentRowFirst_inWatchOrder_unknownIdsDropped_numbersKept() {
        val rows = LiveTvData.buildRows(server, kids = false, recentIds = listOf("disney", "gone", "f1"))
        assertEquals(LiveTvData.RECENT_ROW, rows[0].name)
        assertEquals(listOf("disney", "f1"), rows[0].channels.map { it.id })
        assertEquals(listOf(301, 101), rows[0].channels.map { it.number })
        assertEquals("Kids", rows[0].channels[0].row)
    }

    @Test
    fun statusBackupAndColours() {
        val rows = LiveTvData.buildRows(server, kids = false, recentIds = emptyList())
        val f1 = rows[0].channels[0]
        assertTrue(f1.hasBackup)
        assertFalse(f1.off)
        assertEquals(0xFFD64545, f1.rowColor)
        assertTrue(rows[1].channels[0].off)
        assertEquals(0xFF2644A6, LiveTvData.parseColor("nonsense"))
        assertEquals(0xFF2644A6, LiveTvData.parseColor(null))
    }
}
