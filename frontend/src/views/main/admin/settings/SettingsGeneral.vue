<template>
  <div>
    <v-container fluid class="vertical-container">
      <v-row v-if="appSettings">
        <v-col cols="6">
          <v-card height="100%">
            <v-card-title class="font-weight-light secondary--text">
              Application
            </v-card-title>
            <v-card-text>
              <v-simple-table>
                <table class="w100">
                  <tr>
                    <td>Giskard Instance URL</td>
                    <td>{{ apiURL }}</td>
                  </tr>
                  <tr>
                    <td>Instance</td>
                    <td>{{ appSettings.generalSettings?.instanceId }}
                      <v-btn v-if="appSettings.isDemoHfSpace" icon @click="unlockModal = true">
                        <v-icon v-if="isUnlocked">mdi-lock-open</v-icon>
                        <v-icon v-else>mdi-lock</v-icon>
                      </v-btn>
                    </td>
                  </tr>
                  <tr>
                    <td>Version</td>
                    <td>{{ appSettings.version }}</td>
                  </tr>
                  <tr>
                    <td>Plan</td>
                    <td>{{ appSettings.planName }}
                      <v-btn icon @click="upgradeModal = true">
                        <v-icon>mdi-arrow-up</v-icon>
                      </v-btn>
                    </td>
                  </tr>
                  <tr>
                    <td>License expiration date</td>
                    <td v-if="mainStore.license && mainStore.license.expiresOn">{{
                        mainStore.license?.expiresOn | date
                      }}
                    </td>
                  </tr>
                  <tr>
                    <td colspan="2">
                      <v-divider class="divider" />
                    </td>
                  </tr>
                  <tr>
                    <td>Last commit</td>
                    <td>{{ appSettings.buildCommitId }}</td>
                  </tr>
                  <tr>
                    <td>Last commit date</td>
                    <td>{{ appSettings.buildCommitTime | date }}</td>
                  </tr>
                  <tr>
                    <td>Build branch</td>
                    <td>{{ appSettings.buildBranch }}</td>
                  </tr>
                </table>
              </v-simple-table>
            </v-card-text>
          </v-card>
        </v-col>
        <v-col v-if="appSettings.generalSettings">
          <v-card height="100%">
            <v-card-title class="font-weight-light secondary--text">
              <span>Usage reporting</span>
              <v-spacer/>
              <v-switch
                  v-model="appSettings.generalSettings.isAnalyticsEnabled"
                  @change="saveGeneralSettings(appSettings.generalSettings)"></v-switch>
            </v-card-title>
            <v-card-text>
              <div class="mb-2">
                <p>Giskard can send usage reports.</p>
                <p>The raw user data is never sent, only metadata. This information helps us improve the product and fix
                  bugs sooner. 🐞</p>
              </div>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>
      <v-row v-if="!mainStore.authAvailable && !appSettings.isDemoHfSpace">
        <v-col>
          <ApiTokenCard />
        </v-col>
        <v-col>
          <ClientInstructionCard :internalHFAccessToken="false" />
        </v-col>
      </v-row>
      <v-row>
        <v-col>
          <v-card>
            <v-card-title class="font-weight-light secondary--text d-flex">
              <span>ML Worker</span>
              <v-spacer />
              <v-tabs class="worker-tabs" v-model="selectedWorkerTab">
                <v-tab :disabled="mlWorkerSettingsLoading" class="worker-tab">
                  <span>external</span>
                  <v-icon v-show="!mlWorkerSettingsLoading" size="10" :color="isWorkerAvailable(false) ? 'green' : 'red'">mdi-circle
                  </v-icon>
                  <v-progress-circular size="20" indeterminate v-show="mlWorkerSettingsLoading" />
                </v-tab>
                <v-tab :disabled="mlWorkerSettingsLoading" class="worker-tab">
                  <span>internal</span>
                  <v-icon v-show="!mlWorkerSettingsLoading" size="10" :color="isWorkerAvailable(true) ? 'green' : 'red'">
                    mdi-circle
                  </v-icon>
                  <v-progress-circular size="20" indeterminate v-show="mlWorkerSettingsLoading" />
                </v-tab>
              </v-tabs>
              <v-btn icon @click="initMLWorkerInfo">
                <v-icon>refresh</v-icon>
              </v-btn>
            </v-card-title>
            <v-card-text>
              <v-alert v-show="!externalWorkerSelected" color="primary" border="left" outlined colored-border icon="warning">Internal ML Worker is only used in demo projects. For other projects use an <span class="font-weight-bold">External ML Worker</span>.
              </v-alert>
              <v-simple-table v-if="currentWorker">
                <table class="w100">
                  <tr>
                    <th style="width: 30%"></th>
                  </tr>
                  <tr>
                    <td>Python version</td>
                    <td class="text-h6">{{ currentWorker.interpreterVersion }}</td>
                  </tr>
                  <tr>
                    <td>Python path</td>
                    <td>{{ currentWorker.interpreter }}</td>
                  </tr>
                  <tr>
                    <td>Giskard client version</td>
                    <td>{{ currentWorker.giskardClientVersion }}</td>
                  </tr>
                  <tr>
                    <td>Host</td>
                    <td>{{ currentWorker.platform?.node }}</td>
                  </tr>
                  <tr>
                    <td>Process id</td>
                    <td>{{ currentWorker.pid }}</td>
                  </tr>
                  <tr>
                    <td>Process start time</td>
                    <td>{{ epochToDate(currentWorker.processStartTime) }}</td>
                  </tr>
                  <tr>
                    <td>Internal ML Worker address</td>
                    <td>{{ currentWorker.mlWorkerId }}</td>
                  </tr>
                  <tr>
                    <td>Architecture</td>
                    <td>{{ currentWorker.platform?.machine }}</td>
                  </tr>
                  <tr>
                    <td>Installed packages</td>
                    <td class="overflow-hidden">
                      <v-text-field class="pt-5" dense v-model="installedPackagesSearch" append-icon="mdi-magnify" label="Search" single-line hide-details clearable></v-text-field>
                      <v-data-table dense :sort-by="['name']" :headers="installedPackagesHeaders" :items="installedPackagesData" :search="installedPackagesSearch"></v-data-table>
                    </td>
                  </tr>
                </table>
              </v-simple-table>
              <v-card-text v-else class="pa-0">
                <span v-show="mlWorkerSettingsLoading">Loading information</span>
                <v-container v-show="!mlWorkerSettingsLoading" class="pa-0">
                  <div v-show="!externalWorkerSelected">
                    <p>Not available. Check that internal ML Worker is running or start it with</p>
                    <CodeSnippet language="bash" :codeContent="`giskard server restart worker`" />
                  </div>
                  <div v-show="externalWorkerSelected">
                    <v-alert color="warning" border="left" outlined colored-border icon="info">No external ML Worker is connected
                    </v-alert>
                    <StartWorkerInstructions />
                  </div>
                </v-container>
              </v-card-text>
            </v-card-text>
            <v-card-actions v-if="currentWorker && !appSettings.isDemoHfSpace">
              <v-col class="text-right">
                <v-btn @click="stopMLWorker()">Stop ML Worker</v-btn>
              </v-col>
            </v-card-actions>
          </v-card>
        </v-col>
      </v-row>
    </v-container>
    <v-dialog v-model="upgradeModal" width="700">
      <PlanUpgradeCard @done="upgradeModal = false" />
    </v-dialog>
    <v-dialog v-model="unlockModal" width="500">
      <v-card>
        <v-card-title v-if="isUnlocked" >Lock Giskard demo Gallery Space</v-card-title>
        <v-card-title v-else >Unlock Giskard demo Gallery Space</v-card-title>
        <v-card-text>
          <v-text-field outlined autofocus v-model="unlockToken" label="Token" type="password"/>
        </v-card-text>
        <v-card-actions>
          <v-spacer/>
          <v-btn color="primary" text @click="switchGalleryUnlockStatus()">
            {{ isUnlocked ? "Lock" : "Unlock" }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import {computed, onBeforeMount, ref, watch} from "vue";
import mixpanel from "mixpanel-browser";
import moment from "moment/moment";
import {useMainStore} from "@/stores/main";
import ApiTokenCard from "@/components/ApiTokenCard.vue";
import PlanUpgradeCard from "@/components/ee/PlanUpgradeCard.vue";
import StartWorkerInstructions from "@/components/StartWorkerInstructions.vue";
import CodeSnippet from "@/components/CodeSnippet.vue";
import ClientInstructionCard from "@/components/ClientInstructionCard.vue";
import {openapi} from "@/api-v2";
import {GeneralSettings, MLWorkerInfoDTO} from "@/generated/client";

const mainStore = useMainStore();

const appSettings = computed(() => mainStore.appSettings);
const currentWorker = ref<MLWorkerInfoDTO | undefined>(undefined);
const allMLWorkerSettings = ref<MLWorkerInfoDTO[]>([]);
const selectedWorkerTab = ref<number>(0);
const mlWorkerSettingsLoading = ref<boolean>(false);
const installedPackagesData = ref<{ name: string, version: string }[]>([]);
const installedPackagesSearch = ref<string>("");

const upgradeModal = ref<boolean>(false);
const isUnlocked = ref<boolean>(false);
const unlockModal = ref<boolean>(false);
const unlockToken = ref<string | undefined>(undefined);

const installedPackagesHeaders = [{ text: 'Name', value: 'name', width: '70%' }, {
  text: 'Version',
  value: 'version',
  width: '30%'
}];


onBeforeMount(async () => {
  await initMLWorkerInfo();
  if (mainStore.appSettings?.isDemoHfSpace) {
    await initGalleryUnlockInfo();
  }
})

async function initGalleryUnlockInfo() {
  try {
    const status = await openapi.galleryUnlock.getUnlockStatus();
    isUnlocked.value = status.unlocked!;
  } catch (error) {
    isUnlocked.value = false;
  }
}

async function switchGalleryUnlockStatus() {
  try {
    const status = await openapi.galleryUnlock.setUnlockStatus({
      galleryUnlockDTO: {
        token: unlockToken.value ? unlockToken.value : "",
        unlocked: !isUnlocked.value,
      }
    });
    isUnlocked.value = status.unlocked!;
  } catch (error) {
  } finally {
    unlockToken.value = undefined;
    unlockModal.value = false;
  }
}

const externalWorkerSelected = computed(() => selectedWorkerTab.value == 0);

watch(() => [externalWorkerSelected.value, allMLWorkerSettings.value], () => {
  if (allMLWorkerSettings.value.length) {
    currentWorker.value = allMLWorkerSettings.value.find(value => value.isRemote === externalWorkerSelected.value) || undefined;
    installedPackagesData.value = (currentWorker.value !== undefined && currentWorker.value?.installedPackages) ?
        Object.entries(currentWorker.value?.installedPackages).map(([key, value]) => ({
          name: key,
          version: value
        })) : [];
  }
}, { deep: true })

function isWorkerAvailable(isInternal: boolean): boolean {
  return allMLWorkerSettings.value.find(value => value.isRemote === !isInternal) !== undefined;
}

async function saveGeneralSettings(settings: GeneralSettings) {
  if (!settings.isAnalyticsEnabled) {
    mixpanel.opt_out_tracking();
  } else {
    mixpanel.opt_in_tracking();
  }
  appSettings.value!.generalSettings = await openapi.settings.saveGeneralSettings({generalSettings: settings});
}

async function initMLWorkerInfo() {
  try {
    currentWorker.value = undefined;
    mlWorkerSettingsLoading.value = true;
    allMLWorkerSettings.value = await openapi.mlWorker.getMLWorkerInfo();
    currentWorker.value = allMLWorkerSettings.value.find(value => value.isRemote === externalWorkerSelected.value) || undefined;
  } catch (error) {
  } finally {
    mlWorkerSettingsLoading.value = false;
  }
}

function epochToDate(epoch: number) {
  return moment.unix(epoch).format('DD/MM/YYYY HH:mm:ss');
}

async function stopMLWorker() {
  await openapi.mlWorker.stopWorker({internal: !externalWorkerSelected.value});
}
</script>

<style lang="scss" scoped>
.worker-tab {
  display: flex;
  justify-content: space-between;
  width: 120px;
}

.giskard-address {
  border: 1px lightgrey dashed;
  padding: 2px;
}

.worker-tabs {
  width: auto;
  flex-grow: 0;
}
</style>
