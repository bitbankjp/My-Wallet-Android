/*
 * Copyright 2011-2012 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package jp.bitcheck.android;

import java.math.BigInteger;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import jp.bitcheck.android.R;
import jp.bitcheck.android.ui.WalletActivity;
import jp.bitcheck.android.util.WalletUtils;

/**
 * @author Andreas Schildbach
 */
public class WalletBalanceWidgetProvider extends AppWidgetProvider {
	@Override
	public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {

		try {
			final WalletApplication application = (WalletApplication) context
					.getApplicationContext();

			if (application.getRemoteWallet() == null)
				return;
		
			BigInteger balance = application.getRemoteWallet().getFinal_balance();

			final String balanceStr = WalletUtils.formatValue(balance);

			for (int i = 0; i < appWidgetIds.length; i++) {
				final int appWidgetId = appWidgetIds[i];

				final RemoteViews views = new RemoteViews(
						context.getPackageName(),
						R.layout.wallet_balance_widget_content);
				views.setTextViewText(R.id.widget_wallet_balance, balanceStr);
				views.setImageViewResource(R.id.widget_app_icon,
						Constants.APP_ICON_RESID);
				final Intent intent = new Intent(context, WalletActivity.class);
				views.setOnClickPendingIntent(R.id.widget_frame,
						PendingIntent.getActivity(context, 0, intent, 0));

				AppWidgetManager.getInstance(context).updateAppWidget(
						appWidgetId, views);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
