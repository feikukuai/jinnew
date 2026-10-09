package com.traework.jygoldenfinger.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** 所有金手指小组件的公共基类：任何刷新事件都统一交给 WidgetUpdater。 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetUpdater.updateAll(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        WidgetUpdater.updateAll(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.updateAll(context)
    }
}

class PointsWidgetProvider : BaseWidgetProvider()
class TasksWidgetProvider : BaseWidgetProvider()
class StatsWidgetProvider : BaseWidgetProvider()
class ArtsWidgetProvider : BaseWidgetProvider()
class ItemsWidgetProvider : BaseWidgetProvider()