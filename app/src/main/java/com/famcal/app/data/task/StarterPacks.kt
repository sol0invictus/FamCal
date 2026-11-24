package com.famcal.app.data.task

import com.famcal.app.data.model.HouseholdTask
import com.famcal.app.data.model.Recurrence.DAILY
import com.famcal.app.data.model.Recurrence.MONTHLY
import com.famcal.app.data.model.Recurrence.WEEKLY
import com.famcal.app.data.model.Recurrence.YEARLY
import com.famcal.app.data.model.TaskArea
import com.famcal.app.data.model.TaskRepeat.AFTER_DONE
import com.famcal.app.data.model.TaskRepeat.SCHEDULE
import java.time.LocalDate

/** A ready-made task; [firstDueInDays] staggers a pack so it doesn't all land on one day. */
data class TaskTemplate(
    val title: String,
    val area: String,
    val repeatMode: String,
    val unit: String,
    val interval: Int,
    val points: Int,
    val firstDueInDays: Long,
) {
    fun toTask(today: LocalDate): HouseholdTask = HouseholdTask(
        title = title,
        area = area,
        repeatMode = repeatMode,
        repeatUnit = unit,
        repeatInterval = interval,
        points = points,
        dueDate = today.plusDays(firstDueInDays).toString(),
    )
}

data class StarterPack(val name: String, val emoji: String, val description: String, val tasks: List<TaskTemplate>)

/** Starter packs offered on the Tasks tab, added in one tap. */
object StarterPacks {
    val all: List<StarterPack> = listOf(
        StarterPack(
            "Home maintenance", "🏠", "Filters, detectors, gutters and other upkeep that's easy to forget",
            listOf(
                TaskTemplate("Replace HVAC filter", TaskArea.UTILITIES, AFTER_DONE, MONTHLY, 3, 2, 7),
                TaskTemplate("Test smoke & CO detectors", TaskArea.UTILITIES, SCHEDULE, MONTHLY, 1, 1, 3),
                TaskTemplate("Replace smoke detector batteries", TaskArea.UTILITIES, SCHEDULE, YEARLY, 1, 2, 30),
                TaskTemplate("Service the HVAC system", TaskArea.UTILITIES, SCHEDULE, YEARLY, 1, 2, 30),
                TaskTemplate("Flush the water heater", TaskArea.UTILITIES, SCHEDULE, YEARLY, 1, 3, 45),
                TaskTemplate("Clean the gutters", TaskArea.YARD, SCHEDULE, MONTHLY, 6, 3, 21),
                TaskTemplate("Clean the dryer vent", TaskArea.LAUNDRY, AFTER_DONE, MONTHLY, 6, 2, 14),
                TaskTemplate("Clean range hood filter", TaskArea.KITCHEN, AFTER_DONE, MONTHLY, 3, 1, 10),
                TaskTemplate("Vacuum refrigerator coils", TaskArea.KITCHEN, AFTER_DONE, MONTHLY, 6, 2, 28),
                TaskTemplate("Check fire extinguisher", TaskArea.KITCHEN, SCHEDULE, YEARLY, 1, 1, 60),
            ),
        ),
        StarterPack(
            "Weekly cleaning", "🧽", "The regular rounds that keep the house tidy",
            listOf(
                TaskTemplate("Take out trash & recycling", TaskArea.KITCHEN, SCHEDULE, WEEKLY, 1, 1, 0),
                TaskTemplate("Vacuum floors", TaskArea.LIVING, SCHEDULE, WEEKLY, 1, 2, 1),
                TaskTemplate("Clean bathrooms", TaskArea.BATHROOM, SCHEDULE, WEEKLY, 1, 3, 2),
                TaskTemplate("Change bed sheets", TaskArea.BEDROOM, AFTER_DONE, WEEKLY, 2, 2, 3),
                TaskTemplate("Mop kitchen floor", TaskArea.KITCHEN, SCHEDULE, WEEKLY, 1, 2, 4),
                TaskTemplate("Dust surfaces", TaskArea.LIVING, SCHEDULE, WEEKLY, 1, 1, 5),
                TaskTemplate("Clean the microwave", TaskArea.KITCHEN, AFTER_DONE, WEEKLY, 2, 1, 6),
            ),
        ),
        StarterPack(
            "Daily routines", "☀️", "Small everyday jobs to share around",
            listOf(
                TaskTemplate("Unload the dishwasher", TaskArea.KITCHEN, SCHEDULE, DAILY, 1, 1, 0),
                TaskTemplate("Wipe kitchen counters", TaskArea.KITCHEN, SCHEDULE, DAILY, 1, 1, 0),
                TaskTemplate("Feed the pets", TaskArea.PETS, SCHEDULE, DAILY, 1, 1, 0),
                TaskTemplate("Walk the dog", TaskArea.PETS, SCHEDULE, DAILY, 1, 1, 0),
            ),
        ),
        StarterPack(
            "Yard & outdoor", "🌿", "Lawn, garden and outside upkeep",
            listOf(
                TaskTemplate("Mow the lawn", TaskArea.YARD, AFTER_DONE, WEEKLY, 1, 3, 2),
                TaskTemplate("Water the plants", TaskArea.YARD, AFTER_DONE, DAILY, 3, 1, 1),
                TaskTemplate("Weed garden beds", TaskArea.YARD, AFTER_DONE, WEEKLY, 2, 2, 5),
                TaskTemplate("Pressure-wash the deck", TaskArea.YARD, SCHEDULE, YEARLY, 1, 4, 60),
            ),
        ),
        StarterPack(
            "Car care", "🚗", "Keep the car running and road-legal",
            listOf(
                TaskTemplate("Check tire pressure", TaskArea.CAR, SCHEDULE, MONTHLY, 1, 1, 7),
                TaskTemplate("Wash the car", TaskArea.CAR, AFTER_DONE, WEEKLY, 2, 2, 5),
                TaskTemplate("Oil change", TaskArea.CAR, AFTER_DONE, MONTHLY, 6, 2, 30),
                TaskTemplate("Rotate tires", TaskArea.CAR, AFTER_DONE, MONTHLY, 6, 2, 60),
                TaskTemplate("Renew car registration", TaskArea.ERRANDS, SCHEDULE, YEARLY, 1, 1, 90),
            ),
        ),
        StarterPack(
            "Kids' chores", "⭐", "Simple jobs kids can earn points for",
            listOf(
                TaskTemplate("Make your bed", TaskArea.BEDROOM, SCHEDULE, DAILY, 1, 1, 0),
                TaskTemplate("Set the table", TaskArea.KITCHEN, SCHEDULE, DAILY, 1, 1, 0),
                TaskTemplate("Tidy your room", TaskArea.BEDROOM, SCHEDULE, WEEKLY, 1, 2, 2),
                TaskTemplate("Put away laundry", TaskArea.LAUNDRY, SCHEDULE, WEEKLY, 1, 2, 3),
            ),
        ),
    )
}
