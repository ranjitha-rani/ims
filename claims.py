import streamlit as st
import pandas as pd
from datetime import date
from db import view_claims_admin, get_claimdetails_using_id, get_customer_using_id, get_plan_using_id, update_claim

def claims(adminId):
    st.title("Claim requests")

    menu = ["View claim requests","Update claim requests"]
    choice = st.selectbox("Choose :", menu)
    
    if choice == "View claim requests" :
        st.subheader("Claim requests")
        st.markdown('***')

        data = view_claims_admin(int(adminId))

        if data: 
            df = pd.DataFrame(data, columns=[
                    'ID', 'Reason', 'Status', 'Date', 'paymentId', 'customerId', 'planId', 'adminId'])
            # with st.expander("View Plans"):
            st.dataframe(df)

    if choice == "Update claim requests":
        st.subheader("Update claim requests :")
        st.markdown('***')

        data2 = view_claims_admin(int(adminId))

        if data2:
            dfx = pd.DataFrame(data2, columns=[
                    'ID', 'Reason', 'Status', 'Date', 'paymentId', 'customerId', 'planId', 'adminId'])
            # with st.expander("View Plans"):
            st.dataframe(dfx)
            list_of_claimId = [i[0] for i in data2]
            selected_claim_id = st.selectbox("Choose claim request to update:", list_of_claimId)
            st.warning("Are you sure you want to update claim request : {}?".format(selected_claim_id))

            if st.checkbox("Yes"):
                resultx = get_claimdetails_using_id(int(selected_claim_id))
                if resultx:
                    cust_id = resultx[0][5]
                    plan_id = resultx[0][6]
                    # st.write(resultx)
                    cus_details = get_customer_using_id(int(cust_id))
                    
                    plan_details = get_plan_using_id(int(plan_id))

                    df2 = pd.DataFrame(cus_details, columns=['ID', 'First Name', 'Last Name','Mobile', 'Email', 'password', 'DOB', 'AGE'])
                    df3 = pd.DataFrame(plan_details, columns=['ID', 'Plan Name', 'Plan Description', 'Plan Price', 'Plan Duration', 'Admin Id'])

                    with st.expander("View Customer details"):
                        df2 = df2.drop('password', axis=1)
                        st.dataframe(df2)
                    
                    with st.expander("View Plan details"):
                        st.dataframe(df3)
                    
                    status = st.text_input("Status of claim request :")

                    if st.checkbox("Update "):
                        update_claim(status, int(selected_claim_id))
                        st.success("Status updated successfully")
                    

