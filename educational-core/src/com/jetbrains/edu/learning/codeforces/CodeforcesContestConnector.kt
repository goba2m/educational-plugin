package com.jetbrains.edu.learning.codeforces

import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.edu.learning.codeforces.CodeforcesNames.CODEFORCES_URL
import com.jetbrains.edu.learning.codeforces.authorization.CodeforcesUserInfo
import com.jetbrains.edu.learning.codeforces.courseFormat.CodeforcesCourse
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.swing.text.html.HTML

object CodeforcesContestConnector {
  private const val TD_TAG = "td"
  private const val TR_TAG = "tr"
  private const val DATA_CONTEST_ID_ATTR = "data-contestId"
  private const val FORMAT_DATE_CLASS = "format-date"
  private const val FORMAT_TIME_CLASS = "format-time"
  private const val DATATABLE_CLASS = "datatable"
  private const val CONTEST_LIST_CLASS = "contestList"
  private const val CONTEST_TABLE_CLASS = "contests-table"

  private val logger = Logger.getInstance(CodeforcesUserInfo::class.java)

  fun getContestIdFromLink(link: String): Int {
    val match = Regex("codeforces.com/contest/(\\d*)").find(link) ?: return -1
    return match.destructured.component1().also { if (it == "") return -1 }.toInt()
  }

  fun getContestURLFromID(id: Int): String = "$CODEFORCES_URL/contest/$id"

  fun getContestId(text: String): Int =
    try {
      Integer.parseInt(text)
    }
    catch (e: NumberFormatException) {
      getContestIdFromLink(text)
    }

  fun getLanguages(contest: Document): List<String>? {
    val supportedLanguages = CodeforcesLanguageProvider.getSupportedLanguages()
    return contest.selectFirst("#programTypeForInvoker")
      ?.select("option")
      ?.map { it.text() }
      ?.filter { language ->
        language in supportedLanguages
      }
  }

  fun getUpcomingContests(document: Document): List<CodeforcesCourse> {
    val upcomingContestList = document.body().getElementsByClass(CONTEST_LIST_CLASS).first() ?: error("Cannot parse $CONTEST_LIST_CLASS")
    val upcomingContests = upcomingContestList.children().find { it.className() == DATATABLE_CLASS }
    if (upcomingContests == null) {
      return emptyList()
    }

    return extractContestInfo(upcomingContests, FORMAT_TIME_CLASS)
  }

  /**
   * Recent contest table goes after upcoming and current contests table.
   * See the page format [com.jetbrains.edu.learning.codeforces.api.CodeforcesService.contestsPage]
   */
  fun getRecentContests(document: Document): List<CodeforcesCourse> {
    val recentContestsParent = document.body().getElementsByClass(CONTEST_TABLE_CLASS).firstOrNull() ?: return emptyList()
    val recentContests = recentContestsParent.getElementsByClass(DATATABLE_CLASS).firstOrNull() ?: return emptyList()

    return extractContestInfo(recentContests, FORMAT_DATE_CLASS)
  }

  private fun parseContestInformation(element: Element, dateClass: String): CodeforcesCourse? {
    return try {
      val contestId = element.attr(DATA_CONTEST_ID_ATTR).toInt()
      val tableRow = element.getElementsByTag(TD_TAG)

      val contestName = (tableRow[0].childNodes().first() as TextNode).text().trim()
      val authors = getAuthors(tableRow[1])
      val startDate = parseDate(tableRow, dateClass)
      val contestLength = tableRow[3].text().split(":").map { it.toLong() }
      val standings = parseStandings(tableRow[4])
      val remainingTime = parseRemainingTime(tableRow[4])
      val registrationLinkElement = tableRow[5].getElementsByClass("red-link").firstOrNull()
      val isRegistrationOpen = registrationLinkElement != null
      val registrationCountdown = parseCountdown(tableRow[5])
      val registrationLink = if (isRegistrationOpen) registrationLinkElement?.attr("href") else null
      val participantsNumber = parseParticipantsNumber(tableRow[5])
      val duration = Duration.ofHours(contestLength[0]).plus(Duration.ofMinutes(contestLength[1]))
      val contestParameters = ContestParameters(contestId,
                                                name = contestName,
                                                endDateTime = startDate + duration,
                                                startDate = startDate,
                                                length = duration,
                                                registrationLink = registrationLink,
                                                registrationCountdown = registrationCountdown,
                                                participantsNumber = participantsNumber,
                                                standingsLink = standings,
                                                remainingTime = remainingTime,
                                                authors = authors)
      CodeforcesCourse(contestParameters)
    }
    catch (e: Exception) {
      logger.warn(e.message)
      null
    }
  }

  private fun getAuthors(element: Element?): List<CodeforcesUserInfo> {
    if (element == null) {
      logger.warn("Authors element is null")
      return emptyList()
    }
    val authorsElementsList = element.getElementsByTag(HTML.Tag.A.toString())
    return authorsElementsList.map {
      CodeforcesUserInfo().apply {
        handle = it.text()
      }
    }
  }

  private fun parseRemainingTime(tableRow: Element): Duration? {
    val countdownElement = tableRow.getElementsByClass("countdown").firstOrNull() ?: return null

    return parseCountdown(countdownElement)
  }

  private fun parseStandings(tableRow: Element): String? {
    val linkElement = tableRow.getElementsByTag("a") ?: return null
    return linkElement.attr("href")
  }

  private fun parseCountdown(tableRow: Element): Duration? {
    val countdownElement = tableRow.getElementsByClass("countdown").firstOrNull() ?: return null
    val countdownTitle = countdownElement.getElementsByAttribute("title").firstOrNull()
    val countdownValue = if (countdownTitle != null) {
      countdownTitle.attr("title")
    }
    else {
      countdownElement.text()
    }
    val dateParts = parseCountdown(countdownValue)
    val hours = dateParts[0].toLong()
    val minutes = dateParts[1].toLong()
    val seconds = dateParts[2].toLong()

    return Duration.ofHours(hours)
      .plus(Duration.ofMinutes(minutes))
      .plus(Duration.ofSeconds(seconds))
  }

  private fun parseCountdown(countdownValue: String): List<String> {
    val dateParts = countdownValue.split(":")
    if (dateParts.size != 3) {
      error("wrong countdown format: '$countdownValue'")
    }
    return dateParts
  }

  private fun parseParticipantsNumber(tableRow: Element): Int {
    val participantsElement = tableRow.getElementsByClass("contestParticipantCountLinkMargin").firstOrNull()
    val participantsTextNode = participantsElement?.textNodes()?.firstOrNull() ?: return 0
    return participantsTextNode.text().trimStart(' ', 'x').toInt()
  }

  private fun parseDate(tableRow: Elements, dateClass: String): ZonedDateTime {
    val dateElement = tableRow[2].getElementsByClass(dateClass).firstOrNull() ?: tableRow[2].getElementsByClass(FORMAT_DATE_CLASS).first()
    val dateLocaleString = tableRow[2].getElementsByClass(dateClass).attr("data-locale")
    val dateLocale = Locale.Builder().setLanguage(dateLocaleString).build()
    val startDateString = dateElement?.text() ?: error("Date string is null")
    val formatter = DateTimeFormatter.ofPattern("MMM/dd/yyyy HH:mm", dateLocale)
    val startDateLocal = LocalDateTime.parse(startDateString, formatter)
    return ZonedDateTime.of(startDateLocal, ZoneId.of("GMT+3")).withZoneSameInstant(ZoneId.systemDefault())
  }

  private fun extractContestInfo(contests: Element, dateClass: String): List<CodeforcesCourse> {
    val contestElements = contests.getElementsByTag(TR_TAG)

    return contestElements
      .filter { element -> element.attr(DATA_CONTEST_ID_ATTR).isNotEmpty() }
      .mapNotNull { parseContestInformation(it, dateClass) }
  }
}