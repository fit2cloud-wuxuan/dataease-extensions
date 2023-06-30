<template xmlns:v-slot="http://www.w3.org/1999/XSL/Transform">
  <div>
    <el-row>
      <el-col>
        <el-form
            ref="DsForm"
            :model="form"
            :rules="rule"
            size="small"
            :disabled="disabled"
            label-width="180px"
            label-position="right"
        >
          <el-form-item :label="$t('host')" prop="configuration.host">
            <el-input v-model="form.configuration.host" autocomplete="off" :placeholder="$t('_ip_address')"/>
          </el-form-item>

          <el-form-item :label="$t('dataBase')" prop="configuration.dataBase">
            <el-input v-model="form.configuration.dataBase" autocomplete="off"
                      :placeholder="$t('please_input_data_base')"/>
          </el-form-item>

          <el-form-item :label="$t('auth_method')">
            <el-select
                v-model="form.configuration.authMethod"
                class="de-select"
            >
              <el-option
                  v-for="item in authMethodList"
                  :key="item.id"
                  :label="item.label"
                  :value="item.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item
              v-if="form.configuration.authMethod === 'kerberos'"
              :label="$t('client_principal')"
          >
            <el-input
                v-model="form.configuration.username"
                autocomplete="off"
            />
          </el-form-item>

          <el-form-item
              v-if="form.configuration.authMethod === 'kerberos'"
              :label="$t('keytab_Key_path')"
          >
            <dePwd v-model="form.configuration.password"/>
            <p>
              {{ $t('kerbers_info') }}
            </p>
          </el-form-item>
          <span v-if="form.configuration.authMethod === 'kerberos'"/>

          <el-form-item
              v-if="form.configuration.authMethod !== 'kerberos'"
              :label="$t('username')">
            <el-input
                v-model="form.configuration.username"
                :placeholder="$t('one_user_name')"
                autocomplete="off"
            />
          </el-form-item>

          <el-form-item
              v-if="form.configuration.authMethod !== 'kerberos'"
              :label="$t('password')">
            <dePwd
                v-model="form.configuration.password"
                :placeholder="$t('input_a_password')"
            />
          </el-form-item>
          <el-form-item :label="$t('datasource.extra_params')"
          >
            <el-input
                v-model="form.configuration.extraParams"
                :placeholder="$t('fu.search_bar.please_input') + $t('datasource.extra_params')"
                autocomplete="off"
            />
          </el-form-item>

          <el-form-item
              :label="$t('port')"
              prop="configuration.port"
          >
            <el-input
                v-model="form.configuration.port"
                autocomplete="off"
                type="number"
                :placeholder="$t('enter_the_port')"
                min="0"
            />
          </el-form-item>
          <span
              class="de-expand de-mar0"
              @click="showPriority = !showPriority"
          >{{ $t('priority') }}<i
              v-if="showPriority"
              class="el-icon-arrow-up"
          />
        <i
            v-else
            class="el-icon-arrow-down"
        /></span>

          <template v-if="showPriority">
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item
                    :label="$t('initial_pool_size')"
                    prop="configuration.initialPoolSize"
                >
                  <el-input-number
                      v-model="form.configuration.initialPoolSize"
                      controls-position="right"
                      autocomplete="off"
                      type="number"
                      :min="0"
                      size="small"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item
                    :label="$t('min_pool_size')"
                    prop="configuration.minPoolSize"
                >
                  <el-input-number
                      v-model="form.configuration.minPoolSize"
                      controls-position="right"
                      autocomplete="off"
                      type="number"
                      :min="0"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item
                    :label="$t('max_pool_size')"
                    prop="configuration.maxPoolSize"
                >
                  <el-input-number
                      v-model="form.configuration.maxPoolSize"
                      controls-position="right"
                      autocomplete="off"
                      type="number"
                      :min="0"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item
                    :label="$t('query_timeout')"
                    prop="configuration.queryTimeout"
                >
                  <el-input
                      v-model="form.configuration.queryTimeout"
                      autocomplete="off"
                      type="number"
                      :min="0"
                  >
                    <template slot="append">{{ $t('panel.second') }}</template>
                  </el-input>
                </el-form-item>
              </el-col>
            </el-row>
          </template>
        </el-form>

      </el-col>
    </el-row>
  </div>
</template>

<script>
import messages from '@/de-base/lang/messages'
import dePwd from "./dePwd.vue";

export default {
  name: "hiveV1",
  components: {dePwd},
  props: {
    method: String,
    request: {},
    response: {},
    editApiItem: {
      type: Boolean,
      default() {
        return false;
      }
    },
    showScript: {
      type: Boolean,
      default: true,
    },
    obj: {
      type: Object,
      default() {
        return {
          configuration: {
            initialPoolSize: 5,
            extraParams: '',
            minPoolSize: 5,
            maxPoolSize: 50,
            maxIdleTime: 30,
            acquireIncrement: 5,
            idleConnectionTestPeriod: 5,
            queryTimeout: 30,
            connectTimeout: 5
          },
          apiConfiguration: []
        }
      }
    },
  },
  data() {
    return {
      showPriority: false,
      rule: {
        'configuration.host': [{required: true, message: this.$t('commons.required'), trigger: 'blur'}],
        'configuration.port': [{required: true, message: this.$t('commons.required'), trigger: 'blur'}],
        'configuration.dataBase': [{required: true, message: this.$t('commons.required'), trigger: 'blur'}],
        'configuration.minPoolSize': [{required: true, message: this.$t('commons.required'), trigger: 'blur'}],
        'configuration.maxPoolSize': [{required: true, message: this.$t('commons.required'), trigger: 'blur'}],
        'configuration.queryTimeout': [{required: true, message: this.$t('commons.required'), trigger: 'blur'}],
        'configuration.initialPoolSize': [{required: true, message: this.$t('commons.required'), trigger: 'blur'}]
      },
      canEdit: false,
      originConfiguration: {},
      height: 500,
      disabledNext: false,
      schemas: [],
      authMethodList: [
        {
          id: 'passwd',
          label: this.$t('password')
        },
        {
          id: 'kerberos',
          label: 'Kerberos'
        }
      ],
    }
  },
  computed: {
    form() {
      return this.obj.form
    },
    disabled() {
      return this.obj.disabled
    }
  },
  created() {
    this.$emit('on-add-languages', messages)
  },
  watch: {},
  methods: {
    executeAxios(url, type, data, callBack) {
      const param = {
        url: url,
        type: type,
        data: data,
        callBack: callBack
      }
      this.$emit('execute-axios', param)
    },
    validate() {
      let status = null;
      this.$refs["DsForm"].validate((val) => {
        if (val) {
          status = true
        } else {
          status = false
        }
      })
      return status
    }
  }
}
</script>

<style scoped>
.ms-query {
  background: #409EFF;
  color: white;
  height: 18px;
  border-radius: 42%;
}

.ms-header {
  background: #409EFF;
  color: white;
  height: 18px;
  border-radius: 42%;
}

.request-tabs {
  margin: 20px;
  min-height: 200px;
}

.ms-el-link {
  float: right;
  margin-right: 45px;
}

.de-expand {
  font-family: PingFang SC;
  font-size: 14px;
  font-weight: 400;
  line-height: 22px;
  color: #3370ff;
  cursor: pointer;
  margin: 12px 0 16px 0;
  display: inline-block;
}
.de-mar0 {
  margin-top: 0;
}
</style>

<style lang="scss" scoped>
.el-input-number {
  width: 100%;
  ::v-deep .el-input__inner {
    text-align: left;
  }
  ::v-deep.el-input-number__decrease,
  ::v-deep.el-input-number__increase {
    background: transparent;
  }
  &.is-disabled {
    .el-input-number__decrease {
      border-right-color: #e4e7ed;
    }
    .el-input-number__increase {
      border-bottom-color: #e4e7ed;
    }
  }
}
</style>