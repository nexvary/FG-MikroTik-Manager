package com.fgmachines.mikrotikmanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AboutDeveloperScreen(
    arabic: Boolean,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        FgBlack,
                        FgDeepNavy,
                        Color(0xFF041827),
                        FgBlack
                    )
                )
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = FgDeepNavy.copy(alpha = 0.96f)
                ),
                border = BorderStroke(1.3.dp, FgSilver),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                Brush.sweepGradient(
                                    listOf(FgBlue, FgCyan, FgMint, FgBlue)
                                ),
                                CircleShape
                            )
                            .padding(3.dp)
                            .background(FgBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "FG",
                            color = FgMint,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            if (arabic) "عن المطور" else "About the developer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = FgWhite
                        )
                        Text(
                            if (arabic) "بسم الله الرحمن الرحيم"
                            else "In the name of Allah, the Most Gracious, the Most Merciful",
                            color = FgMint,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            AccentInfoCard(
                accent = FgBlue,
                icon = {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = null,
                        tint = FgBlue
                    )
                },
                title = if (arabic) "المطور" else "Developer",
                body = if (arabic) {
                    "تم تطوير البرنامج بواسطة FG Machines. المطور الرئيسي: علاء محمد. وهذا البرنامج مجاني بالكامل لوجه الله."
                } else {
                    "Developed by FG Machines. Lead developer: Alaa Mohamed. This program is completely free for the sake of Allah."
                }
            )
        }

        item {
            AccentInfoCard(
                accent = FgBlue,
                icon = {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = FgBlue
                    )
                },
                title = if (arabic) "FG MikroTik Manager" else "FG MikroTik Manager",
                body = if (arabic) {
                    "تطبيق لإدارة MikroTik وإنشاء وإدارة كروت HotSpot وUser Manager وPPPoE بواجهة رسومية مبسطة."
                } else {
                    "A graphical MikroTik management and voucher application for HotSpot, User Manager and PPPoE."
                }
            )
        }

        item {
            DeveloperLinkButton(
                title = if (arabic) "الموقع الرسمي · fgmachines.org" else "Official website · fgmachines.org",
                subtitle = "https://fgmachines.org",
                accent = FgCyan,
                icon = {
                    Icon(Icons.Outlined.Public, contentDescription = null, tint = FgCyan)
                },
                onClick = { uriHandler.openUri("https://fgmachines.org") }
            )
        }

        item {
            DeveloperLinkButton(
                title = if (arabic) "رابط الحساب الشخصي" else "Personal account",
                subtitle = if (arabic) "فتح الحساب على Facebook" else "Open personal Facebook account",
                accent = FgMint,
                icon = {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = FgMint)
                },
                onClick = {
                    uriHandler.openUri("https://www.facebook.com/share/1EKVAyZZ2C/")
                }
            )
        }

        item {
            DeveloperLinkButton(
                title = if (arabic) "رابط الصفحة التجارية" else "Business page",
                subtitle = if (arabic) "فتح صفحة FG Machines" else "Open FG Machines business page",
                accent = FgAmber,
                icon = {
                    Icon(Icons.Outlined.Business, contentDescription = null, tint = FgAmber)
                },
                onClick = {
                    uriHandler.openUri("https://www.facebook.com/share/1T7r3WpH8Y/")
                }
            )
        }


    }
}

@Composable
private fun AccentInfoCard(
    accent: Color,
    icon: @Composable () -> Unit,
    title: String,
    body: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = FgPanel.copy(alpha = 0.92f)
        ),
        border = BorderStroke(1.5.dp, accent),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FgWhite
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = FgSilver
                )
            }
        }
    }
}

@Composable
private fun DeveloperLinkButton(
    title: String,
    subtitle: String,
    accent: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, accent),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = FgWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    color = FgSilverMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(
                Icons.Outlined.OpenInNew,
                contentDescription = null,
                tint = accent
            )
        }
    }
}
