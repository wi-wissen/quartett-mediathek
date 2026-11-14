package net.wi7.quartettmediathek.tv.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import net.wi7.quartettmediathek.subscription.Subscription
import net.wi7.quartettmediathek.tv.R
import net.wi7.quartettmediathek.tv.ui.mvwExpression
import net.wi7.quartettmediathek.tv.ui.options
import net.wi7.quartettmediathek.tv.ui.theme.QColors
import net.wi7.quartettmediathek.tv.ui.theme.QType
import net.wi7.quartettmediathek.tv.ui.withOptions

/**
 * "Adjust subscription": four toggles and the resulting MediathekViewWeb expression as text.
 * [subscription] is the live value; every toggle is saved at once through [onChange].
 */
@Composable
fun SubscriptionDialog(subscription: Subscription, onChange: (Subscription) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val o = subscription.options()
    val base = subscription.topics.firstOrNull() ?: subscription.title
    val broadcasters = subscription.broadcasters.joinToString(", ") { it.displayName }
    QDialog(
        title = stringResource(R.string.action_adjust_subscription),
        subtitle = stringResource(R.string.subscription_dialog_subtitle, subscription.title, broadcasters),
        onDismiss = onDismiss, width = 410.dp, initialFocus = first,
    ) {
        MenuRow(
            title = stringResource(R.string.sub_opt_full), description = stringResource(R.string.sub_opt_full_desc),
            toggle = o.fullEpisodesOnly, modifier = Modifier.focusRequester(first),
            onClick = { onChange(subscription.withOptions(o.copy(fullEpisodesOnly = !o.fullEpisodesOnly))) },
        )
        MenuRow(
            title = stringResource(R.string.sub_opt_related, base), description = stringResource(R.string.sub_opt_related_desc),
            toggle = o.relatedTopics,
            onClick = { onChange(subscription.withOptions(o.copy(relatedTopics = !o.relatedTopics))) },
        )
        MenuRow(
            title = stringResource(R.string.sub_opt_ad), description = stringResource(R.string.sub_opt_variant_desc),
            toggle = o.audioDescription,
            onClick = { onChange(subscription.withOptions(o.copy(audioDescription = !o.audioDescription, signLanguage = false))) },
        )
        MenuRow(
            title = stringResource(R.string.sub_opt_dgs), description = stringResource(R.string.sub_opt_variant_desc),
            toggle = o.signLanguage,
            onClick = { onChange(subscription.withOptions(o.copy(signLanguage = !o.signLanguage, audioDescription = false))) },
        )
        Row(Modifier.padding(start = 11.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sub_expression), style = QType.menuSmall, color = QColors.Muted)
            Text(
                mvwExpression(subscription), style = QType.mono, color = QColors.Foreground, maxLines = 2,
                modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(3.dp)).background(QColors.Surface2).padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        MenuRow(title = stringResource(R.string.action_done), icon = R.drawable.ic_check, onClick = onDismiss)
    }
}
