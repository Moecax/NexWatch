package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.NumberStepper
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.SegmentedControl
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.core.watchapi.UserProfile
import com.nexwatch.feature.onboarding.ProfileInput

/** design-prompt.md Batch 1 #2 — Your profile. */
@Composable
fun ProfileScreen(
    profile: ProfileInput,
    onSexChanged: (UserProfile.Sex) -> Unit,
    onAgeChanged: (Int) -> Unit,
    onHeightChanged: (Int) -> Unit,
    onWeightChanged: (Int) -> Unit,
    onContinue: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        EntranceItem(index = 0) {
            Text("Your profile", style = MaterialTheme.typography.headlineSmall)
        }
        EntranceItem(index = 1) {
            Text(
                "The watch uses this for calorie and distance calculations.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        }
        EntranceItem(index = 2) {
            SegmentedControl(
                options = listOf("Male", "Female"),
                selectedIndex = if (profile.sex == UserProfile.Sex.MALE) 0 else 1,
                onSelected = { onSexChanged(if (it == 0) UserProfile.Sex.MALE else UserProfile.Sex.FEMALE) },
            )
        }
        EntranceItem(index = 3) {
            NumberStepper("Age", profile.age, "yrs", onAgeChanged, range = 10..100)
        }
        EntranceItem(index = 4) {
            NumberStepper("Height", profile.heightCm, "cm", onHeightChanged, range = 100..230)
        }
        EntranceItem(index = 5) {
            NumberStepper("Weight", profile.weightKg, "kg", onWeightChanged, range = 30..200)
        }
        EntranceItem(index = 6, modifier = Modifier.padding(top = 24.dp)) {
            PrimaryButton(text = "Continue", onClick = onContinue)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    WatchTheme {
        PremiumBackground {
            ProfileScreen(ProfileInput(), {}, {}, {}, {}, {})
        }
    }
}
